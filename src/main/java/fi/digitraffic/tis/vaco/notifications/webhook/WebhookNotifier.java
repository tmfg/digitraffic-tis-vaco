package fi.digitraffic.tis.vaco.notifications.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.api.model.Link;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.db.model.notifications.SubscriptionRecord;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.db.repositories.ContextRepository;
import fi.digitraffic.tis.vaco.db.repositories.SubscriptionsRepository;
import fi.digitraffic.tis.vaco.http.VacoHttpClient;
import fi.digitraffic.tis.vaco.http.model.NotificationResponse;
import fi.digitraffic.tis.vaco.notifications.Notifier;
import fi.digitraffic.tis.vaco.notifications.model.ImmutableEntryCompletePayload;
import fi.digitraffic.tis.vaco.notifications.model.ImmutableNotification;
import fi.digitraffic.tis.vaco.notifications.model.Notification;
import fi.digitraffic.tis.vaco.notifications.model.NotificationType;
import fi.digitraffic.tis.vaco.notifications.model.SubscriptionType;
import fi.digitraffic.tis.vaco.packages.PackagesController;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder.on;

@Component
public class WebhookNotifier implements Notifier {

    private final ContextRepository contextRepository;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final CompanyRepository companyRepository;

    private final VacoHttpClient httpClient;

    private final ObjectMapper objectMapper;

    private final PackagesService packagesService;
    private final RecordMapper recordMapper;
    private final SubscriptionsRepository subscriptionsRepository;

    private final TaskService taskService;

    private final VacoProperties vacoProperties;

    public WebhookNotifier(CompanyRepository companyRepository,
                           VacoHttpClient httpClient,
                           ObjectMapper objectMapper,
                           VacoProperties vacoProperties,
                           TaskService taskService,
                           PackagesService packagesService,
                           RecordMapper recordMapper,
                           ContextRepository contextRepository,
                           SubscriptionsRepository subscriptionsRepository) {
        this.companyRepository = Objects.requireNonNull(companyRepository);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.taskService = Objects.requireNonNull(taskService);
        this.packagesService = Objects.requireNonNull(packagesService);
        this.recordMapper = Objects.requireNonNull(recordMapper);
        this.contextRepository = Objects.requireNonNull(contextRepository);
        this.subscriptionsRepository = Objects.requireNonNull(subscriptionsRepository);
    }

    @Override
    public void notifyEntryComplete(EntryRecord entry) {
        Optional<CompanyRecord> optResource = companyRepository.findByBusinessId(entry.businessId());

        if (optResource.isPresent()) {
            CompanyRecord resource = optResource.get();

            List<SubscriptionRecord> subscriptions = subscriptionsRepository.findSubscriptionsForResource(SubscriptionType.WEBHOOK, resource);

            if (subscriptions.isEmpty()) {
                logger.debug("No WebHook listeners configured for entry {}'s organization {}", entry.publicId(), entry.businessId());
            } else {
                subscriptions.forEach(subscription -> {
                    CompanyRecord subscriber = companyRepository.findById(subscription.subscriberId());

                    Notification entryCompleteNotification = ImmutableNotification.builder()
                        .name(NotificationType.ENTRY_COMPLETE_V1.getTypeName())
                        .payload(ImmutableEntryCompletePayload.builder()
                            .entry(asEntry(entry))
                            .packages(packagesAsTaskGroupedLinks(entry))
                            .build())
                        .build();
                    if (subscriber.notificationWebhookUri() != null) {
                        String webhookUri = subscriber.notificationWebhookUri();
                        try {
                            CompletableFuture<NotificationResponse> value = httpClient.sendWebhook(webhookUri, objectMapper
                                .writerWithView(DataVisibility.Webhook.class)
                                .writeValueAsBytes(entryCompleteNotification));
                            value.join();
                            logger.info(
                                "Webhook {} notification sent for entry {} to subscriber {} of resource {}",
                                entryCompleteNotification.name(),
                                entry.publicId(),
                                subscriber.businessId(),
                                resource.businessId());
                        } catch (JsonProcessingException e) {
                            logger.warn(
                                "Failed to map WebHook payload to JSON when trying to send {} for entry {} to subscriber {} of resource {}",
                                entryCompleteNotification.name(),
                                entry.publicId(),
                                subscriber.businessId(),
                                resource.businessId(),
                                e);
                        }
                    } else {
                        logger.warn(
                            "No WebHook notification URI set for {}, cannot send {} for entry {} to subscriber {} of resource {}",
                            subscriber.businessId(),
                            entryCompleteNotification.name(),
                            entry.publicId(),
                            subscriber.businessId(),
                            resource.businessId()
                        );
                    }
                });
            }
        } else {
            logger.warn("Couldn't find company by business id {} for entry {}", entry.businessId(), entry.publicId());
        }
    }

    private @NotNull ImmutableEntry asEntry(EntryRecord entry) {
        return recordMapper.toEntryBuilder(entry, contextRepository.find(entry), Optional.empty())
            .tasks(taskService.findTasks(entry))
            .build();
    }

    private Map<String, Map<String, Link>> packagesAsTaskGroupedLinks(EntryRecord entry) {
        return Streams.collect(taskService.findTasks(entry), Task::name, task -> {
            List<Package> packages = packagesService.findAvailablePackages(task, entry.publicId());
            return Streams.collect(packages, Package::name, p -> packageToLink(entry, task, p));
        });
    }

    private Link packageToLink(EntryRecord entry, Task task, Package aPackage) {
        return Link.to(
            vacoProperties.contextUrl(),
            RequestMethod.GET,
            builder -> builder.withMethodCall(on(PackagesController.class).fetchPackage(entry.publicId(), task.name(), aPackage.name(), null)));
    }
}
