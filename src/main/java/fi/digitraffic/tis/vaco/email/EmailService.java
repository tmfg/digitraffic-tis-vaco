package fi.digitraffic.tis.vaco.email;

import com.google.common.annotations.VisibleForTesting;
import fi.digitraffic.tis.html.ContentBuilder;
import fi.digitraffic.tis.html.Element;
import fi.digitraffic.tis.html.HtmlBuildOptions;
import fi.digitraffic.tis.html.HtmlBuilder;
import fi.digitraffic.tis.html.HtmlContent;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.email.mapper.MessageMapper;
import fi.digitraffic.tis.vaco.email.model.ImmutableMessage;
import fi.digitraffic.tis.vaco.email.model.ImmutableRecipients;
import fi.digitraffic.tis.vaco.email.model.Message;
import fi.digitraffic.tis.vaco.email.model.Recipients;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.service.CompanyService;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SesException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

public class EmailService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final VacoProperties vacoProperties;
    private final MessageMapper messageMapper;
    private final SesClient sesClient;
    private final EmailRepository emailRepository;
    private final CompanyService companyService;

    public EmailService(VacoProperties vacoProperties,
                        MessageMapper messageMapper,
                        SesClient sesClient,
                        EmailRepository emailRepository,
                        CompanyService companyService) {
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.messageMapper = Objects.requireNonNull(messageMapper);
        this.sesClient = Objects.requireNonNull(sesClient);
        this.emailRepository = Objects.requireNonNull(emailRepository);
        this.companyService = Objects.requireNonNull(companyService);
    }

    public void sendMessage(Recipients recipients, Message message) {
        try {
            logger.debug("Attempting to send an email through Amazon SES " + "using the AWS SDK for Java...");
            messageMapper.toSendEmailRequests(recipients, message).forEach(sesClient::sendEmail);

        } catch (SesException e) {
            logger.error("Failed to send email", e);
        }
    }

    @Scheduled(cron = "${vaco.scheduling.weekly-feed-status.cron}")
    public void weeklyFeedStatus() {
        List<Company> companies = companyService.listAllWithEntries();
        companies.forEach(this::sendFeedStatusEmail);
    }

    @VisibleForTesting
    protected void sendFeedStatusEmail(Company company) {
        if (logger.isInfoEnabled()) {
            logger.info("Processing weekly feed summary email for {} ({}) contact list {}",
                company.name(),
                company.businessId(),
                company.contactEmails());
        }
        List<ImmutableEntry> latestEntries = emailRepository.findLatestEntries(company);
        if (latestEntries.isEmpty()) {

        } else {
            Locale locale = new Locale(company.language());
            ResourceBundle translations = ResourceBundle.getBundle("emails/feedStatusEmail", locale);

            Recipients recipients = ImmutableRecipients.builder()
                .addAllCc(company.contactEmails())
                .build();
            Message message = ImmutableMessage.builder()
                .subject(translations.getString("email.subject"))
                .body(createFeedStatusEmailHtml(translations, latestEntries))
                .build();
            sendMessage(recipients, message);
        }
    }

    private String createFeedStatusEmailHtml(ResourceBundle translations, List<ImmutableEntry> entries) {
        HtmlBuilder builder = new HtmlBuilder();
        HtmlBuildOptions buildOptions = new HtmlBuildOptions(0, false);

        builder.html5doctype()
            .content(c -> html5EmailTemplate(c,
                translations.getString("email.subject"),
                c.element("body")
                    .children(
                        c.element("div")
                            .children(c.element("h1")
                                .text(translations.getString("message.title"))),
                        c.element("div")
                                .text(translations.getString("message.body")),
                        c.element("div")
                            .children(table(c, translations, entries))
                    )));

        return builder.build(buildOptions);
    }

    private Element table(ContentBuilder c, ResourceBundle translations, List<ImmutableEntry> entries) {
        Element headers = c.element("tr")
            .children(
                c.element("th").text(translations.getString("message.feeds.labels.feed")),
                c.element("th").text(translations.getString("message.feeds.labels.format")),
                c.element("th").text(translations.getString("message.feeds.labels.badge")),
                c.element("th").text(translations.getString("message.feeds.labels.link")));
        List<HtmlContent> rows = Streams.collect(entries, e -> c.element("tr")
            .children(
                c.element("td").text(e.name()),
                c.element("td").text(e.format()),
                c.element("td").text("-"),
                c.element("td").children(link(c, translations, e))));
        return c.element("table")
            .children(headers)
            .children(rows);
    }

    private HtmlContent link(ContentBuilder c, ResourceBundle translations, Entry e) {
        String uiBaseUrl = "local".equals(vacoProperties.environment())
            ? "http://localhost:5173"
            : vacoProperties.baseUrl();
        return c.element("a")
            .attribute("href", uiBaseUrl + "/ui/ticket/info/" + e.publicId())
            .text(translations.getString("message.feeds.entries.link"));
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
}
