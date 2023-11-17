package fi.digitraffic.tis.vaco.email;

import fi.digitraffic.tis.vaco.email.mapper.MessageMapper;
import fi.digitraffic.tis.vaco.email.model.ImmutableMessage;
import fi.digitraffic.tis.vaco.email.model.ImmutableRecipients;
import fi.digitraffic.tis.vaco.email.model.Message;
import fi.digitraffic.tis.vaco.email.model.Recipients;
import fi.digitraffic.tis.vaco.organization.model.Organization;
import fi.digitraffic.tis.vaco.organization.service.OrganizationService;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SesException;

import java.util.List;
import java.util.Objects;

public class EmailService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final MessageMapper messageMapper;
    private final SesClient sesClient;
    private final EmailRepository emailRepository;
    private final OrganizationService organizationService;

    public EmailService(MessageMapper messageMapper,
                        SesClient sesClient,
                        EmailRepository emailRepository,
                        OrganizationService organizationService) {
        this.messageMapper = Objects.requireNonNull(messageMapper);
        this.sesClient = Objects.requireNonNull(sesClient);
        this.emailRepository = Objects.requireNonNull(emailRepository);
        this.organizationService = Objects.requireNonNull(organizationService);
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
        List<Organization> organizations = organizationService.listAllWithEntries();
        organizations.forEach(organization -> sendFeedStatusEmail(organization));
    }

    private void sendFeedStatusEmail(Organization organization) {
        List<ImmutableEntry> latestEntries = emailRepository.findLatestEntries(organization);
        Recipients recipients = ImmutableRecipients.builder()
            //.addAllCc(organization.contactEmails())
            .build();
        Message message = ImmutableMessage.builder()
            .subject("Feed Status Email") // TODO: localization
            //.body(locateEmailTemplate(organization.language(), "feed_status_email"))
            .build();
        sendMessage(recipients, message);
    }
}
