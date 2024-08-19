package fi.digitraffic.tis.vaco.notifications.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.http.VacoHttpClient;
import fi.digitraffic.tis.vaco.notifications.Notifier;
import fi.digitraffic.tis.vaco.notifications.model.ImmutableEntryCompletePayload;
import fi.digitraffic.tis.vaco.notifications.model.ImmutableNotification;
import fi.digitraffic.tis.vaco.notifications.model.Notification;
import fi.digitraffic.tis.vaco.notifications.model.NotificationType;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class WebhookNotifier implements Notifier {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final CompanyRepository companyRepository;

    private final VacoHttpClient httpClient;

    private final ObjectMapper objectMapper;

    public WebhookNotifier(CompanyRepository companyRepository,
                           VacoHttpClient httpClient,
                           ObjectMapper objectMapper) {
        this.companyRepository = Objects.requireNonNull(companyRepository);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public void notifyEntryComplete(Entry entry) {
        companyRepository.findByBusinessId(entry.businessId())
            .map(company -> {
                if (company.notificationWebhookUri() != null) {
                    String webhookUri = company.notificationWebhookUri();
                    Notification entryCompleteNotification = ImmutableNotification.builder()
                        .name(NotificationType.ENTRY_COMPLETE_V1.getTypeName())
                        .payload(ImmutableEntryCompletePayload.builder()
                            .publicId(entry.publicId())
                            .build())
                        .build();
                    try {
                        return httpClient.sendWebhook(webhookUri, objectMapper.writeValueAsBytes(entryCompleteNotification));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    logger.debug("No Webhook notification URI set for {}", company.businessId());
                }
                return null; // TODO: something real from here
            });
    }
}
