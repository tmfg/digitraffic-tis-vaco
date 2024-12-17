package fi.digitraffic.tis.vaco.badges;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.aws.sqs.SqsListener;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.JobMessage;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.model.ImmutableResultMessage;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import fi.digitraffic.tis.vaco.rules.model.ValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.summary.SummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

@Component
public class TestListener extends SqsListener {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final EntryService entryService;
    private final QueueHandlerService queueHandlerService;
    private final ConcurrentMap<String, List<JobMessage>> processingMessages = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Function<ValidationRuleJobMessage, ResultMessage>> resultConverters = new ConcurrentHashMap<>();

    public TestListener(MessagingService messagingService, ObjectMapper objectMapper,
                        EntryService entryService, QueueHandlerService queueHandlerService,
                        TaskService taskService, SummaryService summaryService) {
        super(messagingService, objectMapper);
        this.entryService = entryService;
        this.queueHandlerService = queueHandlerService;
    }

    @Scheduled(initialDelayString = "${vaco.scheduling.findings.poll-rate}", fixedRateString = "${vaco.scheduling.findings.poll-rate}")
    public void handleGtfsCanonical() {
        listenValue(MessageQueue.RULE_PROCESSING.munge(RuleName.GTFS_CANONICAL), ValidationRuleJobMessage.class, this::handleGtfs);
    }

    private CompletableFuture<Boolean> handleGtfs(ValidationRuleJobMessage message) {

        ResultMessage resultMessage = defaultConverter(message);

        return CompletableFuture.supplyAsync(() -> {
            logger.debug("Got ResultMessage {}", resultMessage);
            processingMessages.computeIfAbsent("rules-processing-gtfs-canonical", k -> new CopyOnWriteArrayList<>()).add(message);
            messagingService.sendMessage(QueueNames.VACO_RULES_RESULTS, resultConverters.getOrDefault(message.entry().publicId(), this::defaultConverter).apply(message));
            return true;
        }).whenComplete((ruleProcessingSuccess, maybeEx) -> {
            if (maybeEx != null) {
                logger.warn("Handling rule result failed due to unhandled exception", maybeEx);
            }
            // resubmit to processing queue to continue general logic
            Optional<Entry> entry = entryService.findEntry(resultMessage.entryId());
            entry.ifPresent(value -> messagingService.submitProcessingJob(ImmutableDelegationJobMessage.builder()
                .entry(queueHandlerService.getEntry(value.publicId()))
                .retryStatistics(ImmutableRetryStatistics.of(5))
                .build()));
        });
    }

    private ResultMessage defaultConverter(ValidationRuleJobMessage jobMessage) {
        return ImmutableResultMessage.of(
            jobMessage.entry().publicId(),
            Objects.requireNonNull(jobMessage.task().id()),
            jobMessage.source(),
            jobMessage.inputs(),
            jobMessage.outputs(),
            Map.of()
        );
    }

    public void setResultConverter(String publicId, Function<ValidationRuleJobMessage, ResultMessage> converter) {
        resultConverters.put(publicId, converter);
    }

    public ConcurrentMap<String, List<JobMessage>> getProcessingMessages() {
        return processingMessages;
    }
}
