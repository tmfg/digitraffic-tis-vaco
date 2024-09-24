package fi.digitraffic.tis.vaco.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import fi.digitraffic.tis.aws.sqs.SqsListener;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.findings.FindingService;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
import fi.digitraffic.tis.vaco.rules.internal.StopsAndQuaysRule;
import fi.digitraffic.tis.vaco.rules.model.ErrorMessage;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import fi.digitraffic.tis.vaco.rules.results.GbfsEnturResultProcessor;
import fi.digitraffic.tis.vaco.rules.results.GtfsCanonicalResultProcessor;
import fi.digitraffic.tis.vaco.rules.results.GtfsToNetexResultProcessor;
import fi.digitraffic.tis.vaco.rules.results.InternalRuleResultProcessor;
import fi.digitraffic.tis.vaco.rules.results.NetexEnturValidatorResultProcessor;
import fi.digitraffic.tis.vaco.rules.results.ResultProcessor;
import fi.digitraffic.tis.vaco.rules.results.SimpleResultProcessor;
import fi.digitraffic.tis.vaco.summary.SummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * SQS listener handles for rules which are implemented as part of VACO.
 */
@Component
public class RuleResultsListener extends SqsListener {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final MessagingService messagingService;
    private final FindingService findingService;
    private final TaskService taskService;
    private final QueueHandlerService queueHandlerService;
    private final EntryService entryService;
    private final NetexEnturValidatorResultProcessor netexEnturValidator;
    private final GbfsEnturResultProcessor gbfsResultProcessor;
    private final GtfsCanonicalResultProcessor gtfsResultProcessor;
    private final SimpleResultProcessor simpleResultProcessor;
    private final InternalRuleResultProcessor internalRuleResultProcessor;
    private final SummaryService summaryService;
    private final GtfsToNetexResultProcessor gtfsToNetexResultProcessor;

    public RuleResultsListener(MessagingService messagingService,
                               FindingService findingService,
                               ObjectMapper objectMapper,
                               QueueHandlerService queueHandlerService,
                               TaskService taskService,
                               EntryService entryService,
                               NetexEnturValidatorResultProcessor netexEnturValidator,
                               GbfsEnturResultProcessor gbfsResultProcessor,
                               GtfsCanonicalResultProcessor gtfsResultProcessor,
                               SimpleResultProcessor simpleResultProcessor,
                               InternalRuleResultProcessor internalRuleResultProcessor,
                               SummaryService summaryService,
                               GtfsToNetexResultProcessor gtfsToNetexResultProcessor) {
        super(messagingService, objectMapper);
        this.messagingService = Objects.requireNonNull(messagingService);
        this.findingService = Objects.requireNonNull(findingService);
        this.queueHandlerService = Objects.requireNonNull(queueHandlerService);
        this.taskService = Objects.requireNonNull(taskService);
        this.entryService = Objects.requireNonNull(entryService);
        this.netexEnturValidator = Objects.requireNonNull(netexEnturValidator);
        this.gbfsResultProcessor = Objects.requireNonNull(gbfsResultProcessor);
        this.gtfsResultProcessor = Objects.requireNonNull(gtfsResultProcessor);
        this.simpleResultProcessor = Objects.requireNonNull(simpleResultProcessor);
        this.internalRuleResultProcessor = Objects.requireNonNull(internalRuleResultProcessor);
        this.summaryService = Objects.requireNonNull(summaryService);
        this.gtfsToNetexResultProcessor = Objects.requireNonNull(gtfsToNetexResultProcessor);
    }

    @Scheduled(fixedRateString = "${vaco.scheduling.findings.poll-rate}")
    public void handleErrorsQueue() {
        listenValue(MessageQueue.ERRORS.getQueueName(), ErrorMessage.class, this::handleErrors);
    }

    private CompletableFuture<Boolean> handleErrors(ErrorMessage errorMessage) {
        return CompletableFuture.supplyAsync(() -> {
            logger.warn("Got ErrorMessage {}", errorMessage);
            findingService.reportFindings(errorMessage.findings());
            return true;
        });
    }

    @Scheduled(fixedRateString = "${vaco.scheduling.dlq.poll-rate}")
    public void handleDeadLetterQueue() {
        listenTree(MessageQueue.DLQ.getQueueName(), this::handleDeadLetter);
    }

    @Scheduled(fixedRateString = "${vaco.scheduling.rules-results.poll-rate}")
    public void handleRuleResultsIngestQueue() {
        listenValue(MessageQueue.RULE_RESULTS_INGEST.getQueueName(), ResultMessage.class, this::handleResult);
    }

    @VisibleForTesting
    protected CompletableFuture<Boolean> handleDeadLetter(JsonNode jsonNode) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Dead letter Queue Message {}", jsonNode);

            Optional<String> taskPublicId = Optional.ofNullable(jsonNode)
                .filter(node -> node.has("task"))
                .map(node -> node.get("task").get("publicId"))
                .map(JsonNode::asText);

            return taskPublicId
                .flatMap(taskService::findTask)
                .map(task -> {
                    taskService.markStatus(task, Status.FAILED);
                    return true;
                }).orElse(false);
        });

    }

    protected CompletableFuture<Boolean> handleResult(ResultMessage resultMessage) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("Got ResultMessage {}", resultMessage);
            return switch (resultMessage.ruleName()) {
                case DownloadRule.PREPARE_DOWNLOAD_TASK -> processDownloadRuleResults(resultMessage);
                case StopsAndQuaysRule.PREPARE_STOPS_AND_QUAYS_TASK -> processStopsAndQuaysResults(resultMessage);
                case RuleName.NETEX_ENTUR -> processResultFromNetexEntur(resultMessage);
                case RuleName.GTFS_CANONICAL -> processResultFromGtfsCanonical(resultMessage);
                case RuleName.NETEX2GTFS_ENTUR -> processNetex2GtfsEntur(resultMessage);
                case RuleName.GTFS2NETEX_FINTRAFFIC -> processGtfs2NetexFintraffic(resultMessage);
                case RuleName.GBFS_ENTUR -> processGbfsEntur(resultMessage);
                default -> {
                    logger.error(
                        "Unexpected rule name detected in queue {}: {}",
                        MessageQueue.RULE_RESULTS_INGEST.getQueueName(),
                        resultMessage.ruleName());
                    yield false;
                }
            };
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

    private boolean processDownloadRuleResults(ResultMessage resultMessage) {
        return processRule(DownloadRule.PREPARE_DOWNLOAD_TASK, resultMessage, internalRuleResultProcessor);
    }

    private boolean processStopsAndQuaysResults(ResultMessage resultMessage) {
        return processRule(StopsAndQuaysRule.PREPARE_STOPS_AND_QUAYS_TASK, resultMessage, internalRuleResultProcessor);
    }

    private boolean processResultFromNetexEntur(ResultMessage resultMessage) {
        return processRule(RuleName.NETEX_ENTUR, resultMessage, netexEnturValidator);
    }

    private boolean processResultFromGtfsCanonical(ResultMessage resultMessage) {
        return processRule(RuleName.GTFS_CANONICAL, resultMessage, gtfsResultProcessor);
    }

    private boolean processNetex2GtfsEntur(ResultMessage resultMessage) {
        return processRule(RuleName.NETEX2GTFS_ENTUR, resultMessage, simpleResultProcessor);
    }

    private boolean processGtfs2NetexFintraffic(ResultMessage resultMessage) {
        return processRule(RuleName.GTFS2NETEX_FINTRAFFIC, resultMessage, gtfsToNetexResultProcessor);
    }

    private boolean processGbfsEntur(ResultMessage resultMessage) {
        return processRule(RuleName.GBFS_ENTUR, resultMessage, gbfsResultProcessor);
    }

    private boolean processRule(String ruleName, ResultMessage resultMessage, ResultProcessor resultProcessor) {
        Optional<Entry> e = entryService.findEntry(resultMessage.entryId());

        if (e.isPresent()) {
            Entry entry = e.get();
            Optional<Task> task = taskService.findTask(entry.publicId(), resultMessage.ruleName());
            return task.map(t -> {
                Task tracked = taskService.trackTask(entry, t, ProcessingState.UPDATE);
                try {
                    logger.info("Processing result from {} for entry {}/task {}", ruleName, entry.publicId(), tracked.name());
                    boolean result = resultProcessor.processResults(resultMessage, entry, tracked);
                    Task taskWithLatestStatus = taskService.findTask(entry.publicId(), resultMessage.ruleName()).get();

                    if (result && Status.isNotCompleted(taskWithLatestStatus.status())) {
                        tracked = taskService.markStatus(entry, tracked, Status.SUCCESS);
                    }
                    return result;
                } finally {
                    summaryService.generateSummaries(entry, t);
                    taskService.trackTask(entry, tracked, ProcessingState.COMPLETE);
                }
            }).orElse(false);
        } else {
            return false;
        }
    }

}
