package fi.digitraffic.tis.vaco.notifications.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.InvalidMappingException;
import fi.digitraffic.tis.vaco.api.model.Link;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.db.repositories.ContextRepository;
import fi.digitraffic.tis.vaco.http.VacoHttpClient;
import fi.digitraffic.tis.vaco.http.model.NotificationResponse;
import fi.digitraffic.tis.vaco.notifications.Notifier;
import fi.digitraffic.tis.vaco.notifications.model.ImmutableEntryCompletePayload;
import fi.digitraffic.tis.vaco.notifications.model.ImmutableNotification;
import fi.digitraffic.tis.vaco.notifications.model.Notification;
import fi.digitraffic.tis.vaco.notifications.model.NotificationType;
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

    private final TaskService taskService;

    private final VacoProperties vacoProperties;

    public WebhookNotifier(CompanyRepository companyRepository,
                           VacoHttpClient httpClient,
                           ObjectMapper objectMapper,
                           VacoProperties vacoProperties,
                           TaskService taskService,
                           PackagesService packagesService,
                           RecordMapper recordMapper,
                           ContextRepository contextRepository) {
        this.companyRepository = Objects.requireNonNull(companyRepository);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.taskService = Objects.requireNonNull(taskService);
        this.packagesService = Objects.requireNonNull(packagesService);
        this.recordMapper = Objects.requireNonNull(recordMapper);
        this.contextRepository = Objects.requireNonNull(contextRepository);
    }

    @Override
    public void notifyEntryComplete(EntryRecord entry) {
        Optional<NotificationResponse> notification = companyRepository.findByBusinessId(entry.businessId())
            .flatMap(company -> {
                if (company.notificationWebhookUri() != null) {
                    String webhookUri = company.notificationWebhookUri();
                    Notification entryCompleteNotification = ImmutableNotification.builder()
                        .name(NotificationType.ENTRY_COMPLETE_V1.getTypeName())
                        .payload(ImmutableEntryCompletePayload.builder()
                            .entry(asEntry(entry))
                            .packages(packagesAsTaskGroupedLinks(entry))
                            .build())
                        .build();
                    try {
                        return Optional.of(httpClient.sendWebhook(webhookUri, objectMapper
                            .writerWithView(DataVisibility.Webhook.class)
                            .writeValueAsBytes(entryCompleteNotification)));
                    } catch (JsonProcessingException e) {
                        throw new InvalidMappingException("Failed to map webhook payload to JSON", e);
                    }
                } else {
                    logger.debug("No Webhook notification URI set for {}", company.businessId());
                    return Optional.empty();
                }
            })
            .map(CompletableFuture::join);

        if (notification.isPresent()) {
            logger.info("Webhook {} notification sent for entry {}/ company {}", NotificationType.ENTRY_COMPLETE_V1, entry.publicId(), entry.businessId());
        }
    }

    private @NotNull ImmutableEntry asEntry(EntryRecord entry) {
        return recordMapper.toEntryBuilder(entry, contextRepository.find(entry))
            .tasks(taskService.findTasks(entry))
            .build();
    }

    private Map<String, Map<String, Link>> packagesAsTaskGroupedLinks(EntryRecord entry) {
        return Streams.collect(taskService.findTasks(entry), Task::name, task -> {
            List<Package> packages = packagesService.findAvailablePackages(task);
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
