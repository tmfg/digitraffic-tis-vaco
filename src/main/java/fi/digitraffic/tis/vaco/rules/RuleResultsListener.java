package fi.digitraffic.tis.vaco.rules;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.utilities.model.ProcessingState;
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
import fi.digitraffic.tis.vaco.rules.internal.SummaryRule;
import fi.digitraffic.tis.vaco.rules.model.ErrorMessage;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import fi.digitraffic.tis.vaco.rules.results.GtfsCanonicalResultProcessor;
import fi.digitraffic.tis.vaco.rules.results.InternalRuleResultProcessor;
import fi.digitraffic.tis.vaco.rules.results.NetexEnturValidatorResultProcessor;
import fi.digitraffic.tis.vaco.rules.results.SimpleResultProcessor;
import fi.digitraffic.tis.vaco.rules.results.ResultProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * SQS listener handles for rules which are implemented as part of VACO.
 */
@Component
public class RuleResultsListener {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final MessagingService messagingService;
    private final FindingService findingService;
    private final ObjectMapper objectMapper;
    private final TaskService taskService;
    private final QueueHandlerService queueHandlerService;
    private final NetexEnturValidatorResultProcessor netexEnturValidator;
    private final GtfsCanonicalResultProcessor gtfsCanonicalValidator;
    private final SimpleResultProcessor simpleResultProcessor;
    private final InternalRuleResultProcessor internalRuleResultProcessor;

    public RuleResultsListener(MessagingService messagingService,
                               FindingService findingService,
                               ObjectMapper objectMapper,
                               QueueHandlerService queueHandlerService,
                               TaskService taskService,
                               NetexEnturValidatorResultProcessor netexEnturValidator,
                               GtfsCanonicalResultProcessor gtfsCanonicalValidator,
                               SimpleResultProcessor simpleResultProcessor,
                               InternalRuleResultProcessor internalRuleResultProcessor) {
        this.messagingService = Objects.requireNonNull(messagingService);
        this.findingService = Objects.requireNonNull(findingService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.queueHandlerService = Objects.requireNonNull(queueHandlerService);
        this.taskService = Objects.requireNonNull(taskService);
        this.netexEnturValidator = Objects.requireNonNull(netexEnturValidator);
        this.gtfsCanonicalValidator = Objects.requireNonNull(gtfsCanonicalValidator);
        this.simpleResultProcessor = Objects.requireNonNull(simpleResultProcessor);
        this.internalRuleResultProcessor = Objects.requireNonNull(internalRuleResultProcessor);
    }

    @Scheduled(fixedRateString = "${vaco.scheduling.findings.poll-rate}")
    public void handleErrorsQueue() {
        listen(MessageQueue.ERRORS.getQueueName(), ErrorMessage.class, this::handleErrors);
    }

    private CompletableFuture<Boolean> handleErrors(ErrorMessage errorMessage) {
        return CompletableFuture.supplyAsync(() -> {
            logger.warn("Got ErrorMessage {}", errorMessage);
            findingService.reportFindings(errorMessage.findings());
            return true;
        });
    }

    @Scheduled(fixedRateString = "${vaco.scheduling.rules-results.poll-rate}")
    public void handleRuleResultsIngestQueue() {
        listen(MessageQueue.RULE_RESULTS_INGEST.getQueueName(), ResultMessage.class, this::handleResult);
    }

    private CompletableFuture<Boolean> handleResult(ResultMessage resultMessage) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("Got ResultMessage {}", resultMessage);
            return switch (resultMessage.ruleName()) {
                case DownloadRule.DOWNLOAD_SUBTASK -> processDownloadRuleResults(resultMessage);
                case StopsAndQuaysRule.STOPS_AND_QUAYS_TASK -> processStopsAndQuaysResults(resultMessage);
                case RuleName.NETEX_ENTUR_1_0_1 -> processResultFromNetexEntur101(resultMessage);
                case RuleName.GTFS_CANONICAL_4_0_0 -> processResultFromGtfsCanonical(RuleName.GTFS_CANONICAL_4_0_0, resultMessage);
                case RuleName.GTFS_CANONICAL_4_1_0 -> processResultFromGtfsCanonical(RuleName.GTFS_CANONICAL_4_1_0, resultMessage);
                case RuleName.NETEX2GTFS_ENTUR_2_0_6 -> processNetex2GtfsEntur206(resultMessage);
                case RuleName.GTFS2NETEX_FINTRAFFIC_1_0_0 -> processGtfs2NetexFintraffic100(resultMessage);
                // SUMMARY_TASK not expected to produce any "result" packages:
                case SummaryRule.SUMMARY_TASK -> true;
                default -> {
                    logger.error(
                        "Unexpected rule name detected in queue {}: {}",
                        MessageQueue.RULE_RESULTS_INGEST.getQueueName(),
                        resultMessage.ruleName());
                    yield false;
                }
            };
        }).thenApply(ruleProcessingSuccess -> {
            if (Boolean.TRUE.equals(ruleProcessingSuccess)) {
                Optional<Entry> entry = queueHandlerService.findEntry(resultMessage.entryId());
                if (entry.isPresent()) {
                    messagingService.submitProcessingJob(ImmutableDelegationJobMessage.builder()
                        .entry(queueHandlerService.getEntry(entry.get().publicId(), true))
                        .retryStatistics(ImmutableRetryStatistics.of(5))
                        .build());
                    return true;
                } else {
                    return false;
                }
            } else {
                // could fork logic at this point for non-successful rule runs
                return false;
            }
        });
    }

    private Boolean processDownloadRuleResults(ResultMessage resultMessage) {
        return processRule(DownloadRule.DOWNLOAD_SUBTASK, resultMessage, internalRuleResultProcessor);
    }

    private Boolean processStopsAndQuaysResults(ResultMessage resultMessage) {
        return processRule(StopsAndQuaysRule.STOPS_AND_QUAYS_TASK, resultMessage, internalRuleResultProcessor);
    }

    private boolean processResultFromNetexEntur101(ResultMessage resultMessage) {
        return processRule(RuleName.NETEX_ENTUR_1_0_1, resultMessage, netexEnturValidator);
    }

    private boolean processResultFromGtfsCanonical(String ruleName, ResultMessage resultMessage) {
        return processRule(ruleName, resultMessage, gtfsCanonicalValidator);
    }

    private Boolean processNetex2GtfsEntur206(ResultMessage resultMessage) {
        return processRule(RuleName.NETEX2GTFS_ENTUR_2_0_6, resultMessage, simpleResultProcessor);
    }

    private boolean processGtfs2NetexFintraffic100(ResultMessage resultMessage) {
        return processRule(RuleName.GTFS2NETEX_FINTRAFFIC_1_0_0, resultMessage, simpleResultProcessor);
    }

    private boolean processRule(String ruleName, ResultMessage resultMessage, ResultProcessor resultProcessor) {
        Optional<Entry> e = queueHandlerService.findEntry(resultMessage.entryId());

        if (e.isPresent()) {
            Entry entry = e.get();
            Optional<Task> task = taskService.findTask(entry.id(), resultMessage.ruleName());
            return task.map(t -> {
                Task tracked = taskService.trackTask(t, ProcessingState.UPDATE);
                try {
                    logger.info("Processing result from {} for entry {}/task {}", ruleName, entry.publicId(), tracked.name());
                    boolean result = resultProcessor.processResults(resultMessage, entry, tracked);
                    if (result) {
                        tracked = taskService.markStatus(tracked, Status.SUCCESS);
                    } else {
                        // TODO: it is unclear if this branch will ever be hit
                        tracked = taskService.markStatus(tracked, Status.WARNINGS);
                    }
                    return result;
                } finally {
                    taskService.trackTask(tracked, ProcessingState.COMPLETE);
                }
            }).orElse(false);
        } else {
            return false;
        }
    }

    private <M, R> void listen(String queueName, Class<M> cls, Function<M, CompletableFuture<R>> function) {
        try {
            List<R> ignored = messagingService.readMessages(queueName)
                .map(m -> {
                    try {
                        logger.trace("Processing message {}", m);
                        M message = objectMapper.readValue(m.body(), cls);
                        return function.apply(message);
                    } catch (JsonProcessingException e) {
                        throw new RuleExecutionException("Failed to deserialize message in queue " + queueName, e);
                    } finally {
                        logger.trace("Deleting message {}", m);
                        messagingService.deleteMessage(queueName, m);
                    }
                })
                .map(CompletableFuture::join)
                .toList();

        } catch (SqsException e) {
            logger.warn("Failed to process messages from queue {}", queueName, e);
        }
    }
}
