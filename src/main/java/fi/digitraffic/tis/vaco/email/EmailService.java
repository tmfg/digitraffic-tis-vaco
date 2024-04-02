package fi.digitraffic.tis.vaco.email;

import com.google.common.annotations.VisibleForTesting;
import fi.digitraffic.tis.html.ContentBuilder;
import fi.digitraffic.tis.html.Element;
import fi.digitraffic.tis.html.HtmlBuildOptions;
import fi.digitraffic.tis.html.HtmlBuilder;
import fi.digitraffic.tis.html.HtmlContent;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.crypt.EncryptionService;
import fi.digitraffic.tis.vaco.email.mapper.MessageMapper;
import fi.digitraffic.tis.vaco.email.model.ImmutableMessage;
import fi.digitraffic.tis.vaco.email.model.ImmutableRecipients;
import fi.digitraffic.tis.vaco.email.model.Message;
import fi.digitraffic.tis.vaco.email.model.Recipients;
import fi.digitraffic.tis.vaco.entries.EntryRepository;
import fi.digitraffic.tis.vaco.featureflags.FeatureFlagsService;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.PersistentEntry;
import fi.digitraffic.tis.vaco.ui.model.ImmutableMagicToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SesException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class EmailService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final VacoProperties vacoProperties;
    private final MessageMapper messageMapper;
    private final SesClient sesClient;
    private final CompanyHierarchyService companyHierarchyService;
    private final FeatureFlagsService featureFlagsService;
    private final EntryRepository entryRepository;
    private final EncryptionService encryptionService;

    public EmailService(VacoProperties vacoProperties,
                        MessageMapper messageMapper,
                        SesClient sesClient,
                        CompanyHierarchyService companyHierarchyService,
                        FeatureFlagsService featureFlagsService,
                        EntryRepository entryRepository,
                        EncryptionService encryptionService) {
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.messageMapper = Objects.requireNonNull(messageMapper);
        this.sesClient = Objects.requireNonNull(sesClient);
        this.companyHierarchyService = Objects.requireNonNull(companyHierarchyService);
        this.featureFlagsService = Objects.requireNonNull(featureFlagsService);
        this.entryRepository = Objects.requireNonNull(entryRepository);
        this.encryptionService = Objects.requireNonNull(encryptionService);
    }

    @VisibleForTesting
    protected void sendMessage(Recipients recipients, Message message) {
        try {
            logger.info("Sending email [{}] to {} recipients", message.subject(), countRecipients(recipients));
            messageMapper.toSendEmailRequests(recipients, message).forEach(sesClient::sendEmail);
        } catch (SesException e) {
            logger.error("Failed to send email", e);
        }
    }

    private int countRecipients(Recipients recipients) {
        return recipients.to().size() + recipients.cc().size() + recipients.bcc().size();
    }

    @Scheduled(cron = "${vaco.scheduling.weekly-feed-status.cron}")
    public void weeklyFeedStatus() {
        try {
            List<Company> companies = companyHierarchyService.listAllWithEntries();
            companies.forEach(this::sendFeedStatusEmail);
        } catch (Exception e) {
            logger.error("Failed to send weekly feed status emails", e);
        }
    }

    @VisibleForTesting
    protected void sendFeedStatusEmail(Company company) {
        if (logger.isInfoEnabled()) {
            logger.info("Processing weekly feed summary email for {} ({}) contact list {}",
                company.name(),
                company.businessId(),
                company.contactEmails());
        }

        if (!featureFlagsService.isFeatureFlagEnabled("emails.feedStatusEmail")) {
            logger.info("Feature flag 'emails.feedStatusEmail' is currently disabled, feed status email sending for {} skipped.", company.businessId());
        } else {
            List<PersistentEntry> latestEntries = entryRepository.findLatestEntries(company);
            if (latestEntries.isEmpty()) {
                logger.debug("No entries available for company '{}', feed status email sending skipped", company.businessId());
            } else {
                Translations translations = resolveTranslations(company, "emails/feedStatusEmail");

                Recipients recipients = ImmutableRecipients.builder()
                    .addAllCc(company.contactEmails())
                    .build();
                Message message = ImmutableMessage.builder()
                    .subject(translations.get("email.subject"))
                    .body(createFeedStatusEmailHtml(translations, latestEntries))
                    .build();
                sendMessage(recipients, message);
            }
        }
    }

    public void notifyEntryComplete(Entry entry) {
        if (!featureFlagsService.isFeatureFlagEnabled("emails.entryCompleteEmail")) {
            logger.info("Feature flag 'emails.entryCompleteEmail' is currently disabled, entry complete email sending for {} skipped.", entry.publicId());
        } else if (!entry.notifications().isEmpty()) {
            logger.debug("Notifying {} entry's {} receivers of entry completion", entry.publicId(), entry.notifications());
            companyHierarchyService.findByBusinessId(entry.businessId()).ifPresent(company -> {
                Translations translations = resolveTranslations(company, "emails/entryCompleteEmail");

                Recipients recipients = ImmutableRecipients.builder()
                    .addAllCc(entry.notifications())
                    .build();
                Message message = ImmutableMessage.builder()
                    .subject(translations.get("email.subject"))
                    .body(createEntryCompleteEmail(translations, entry))
                    .build();
                sendMessage(recipients, message);
            });
        } else {
            logger.debug("Entry {} does not have per-entry notification emails set, skipping notification email for entry completion", entry.publicId());
        }
    }

    private String createEntryCompleteEmail(Translations translations, Entry entry) {
        HtmlBuilder builder = new HtmlBuilder();
        HtmlBuildOptions buildOptions = new HtmlBuildOptions(0, false);

        builder.html5doctype()
            .content(c -> html5EmailTemplate(c,
                translations.get("email.subject"),
                c.element("body")
                    .children(
                        c.element("div")
                            .children(c.element("h1")
                                .text(translations.get("message.title"))),
                        c.element("div")
                            .text(translations.get("message.body")),
                        c.element("div")
                            .children(
                                link(c, "/ui/data/" + entry.publicId(), translations.get("entry.link.text")))
                    )));

        return builder.build(buildOptions);
    }

    private String createFeedStatusEmailHtml(Translations translations, List<PersistentEntry> entries) {
        HtmlBuilder builder = new HtmlBuilder();
        HtmlBuildOptions buildOptions = new HtmlBuildOptions(0, false);

        builder.html5doctype()
            .content(c -> html5EmailTemplate(c,
                translations.get("email.subject"),
                c.element("body")
                    .children(
                        c.element("div")
                            .children(c.element("h1")
                                .text(translations.get("message.title"))),
                        c.element("div")
                                .text(translations.get("message.body")),
                        c.element("div")
                            .children(table(c, translations, entries))
                    )));

        return builder.build(buildOptions);
    }

    private Element table(ContentBuilder c, Translations translations, List<PersistentEntry> entries) {
        Element headers = c.element("tr")
            .children(
                c.element("th").text(translations.get("message.feeds.labels.feed")),
                c.element("th").text(translations.get("message.feeds.labels.format")),
                c.element("th").text(translations.get("message.feeds.labels.badge")),
                c.element("th").text(translations.get("message.feeds.labels.link")));
        List<HtmlContent> rows = Streams.collect(entries, e -> c.element("tr")
            .children(
                c.element("td").text(e.name()),
                c.element("td").text(e.format()),
                c.element("td").children(badge(c, e)),
                c.element("td")
                    .children(
                        link(c,
                            "/ui/data/" + e.publicId() + "?magic=" + encryptionService.encrypt(ImmutableMagicToken.of(e.publicId())),
                            translations.get("message.feeds.entries.link")))));
        return c.element("table")
            .children(headers)
            .children(rows);
    }

    private Element badge(ContentBuilder c, PersistentEntry e) {
        return c.element("img", Map.of("src", vacoProperties.baseUrl() + "/api/badge/" + e.publicId()));
    }

    private HtmlContent link(ContentBuilder c,
                             String linkPath,
                             String linkText) {
        String uiBaseUrl = "local".equals(vacoProperties.environment())
            ? "http://localhost:5173"
            : vacoProperties.baseUrl();
        return c.element("a")
            .attribute("href", uiBaseUrl + linkPath)
            .text(linkText);
    }

    private Element html5EmailTemplate(ContentBuilder c, String title, Element body) {
        return c.element("html")
            .attribute("lang", "en")
            .attribute("xmlns", "http://www.w3.org/1999/xhtml")
            .attribute("xmlns:v", "urn:schemas-microsoft-com:vml")
            .attribute("xmlns:o", "urn:schemas-microsoft-com:office:office")
            .children(
                templateHead(c,
                    title,
                    c.element("style").children(
                        c.raw("@media only screen and (max-width: 620px) {}"))),
                body);
    }

    private static Element templateHead(ContentBuilder c, String title, Element style) {
        return c.element("head")
            .children(
                c.element("meta")
                    .attribute("name", "viewport")
                    .attribute("content", "width=device-width, initial-scale=1.0"),
                c.element("meta")
                    .attribute("http-equiv", "Content-Type")
                    .attribute("content", "text/html; charset=UTF-8"),
                c.element("title")
                    .text(title),
                style);
    }

    private static Translations resolveTranslations(Company company, String bundleName) {
        return new Translations(company.language(), bundleName);
    }
}
