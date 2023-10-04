package fi.digitraffic.tis.vaco.rules;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.TempFiles;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.rules.model.ErrorMessage;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import fi.digitraffic.tis.vaco.rules.model.ValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.rules.validation.gtfs.CanonicalGtfsValidatorRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * SQS listener handles for rules which are implemented as part of VACO.
 */
@Service
public class RuleListenerService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final MessagingService messagingService;

    private final ErrorHandlerService errorHandlerService;

    private final ObjectMapper objectMapper;
    private final S3Client s3Client;
    private final CanonicalGtfsValidatorRule canonicalGtfsValidatorRule;
    private final VacoProperties vacoProperties;
    private final QueueHandlerService queueHandlerService;
    private final PackagesService packagesService;
    private final TaskService taskService;

    public RuleListenerService(MessagingService messagingService,
                               ErrorHandlerService errorHandlerService,
                               ObjectMapper objectMapper,
                               S3Client s3Client,
                               CanonicalGtfsValidatorRule canonicalGtfsValidatorRule,
                               VacoProperties vacoProperties, QueueHandlerService queueHandlerService, PackagesService packagesService, TaskService taskService) {
        this.messagingService = messagingService;
        this.errorHandlerService = errorHandlerService;
        this.objectMapper = objectMapper;
        this.s3Client = s3Client;
        this.canonicalGtfsValidatorRule = canonicalGtfsValidatorRule;
        this.vacoProperties = vacoProperties;
        this.queueHandlerService = queueHandlerService;
        this.packagesService = packagesService;
        this.taskService = taskService;
    }

    @Scheduled(fixedRateString = "${vaco.scheduling.canonical-gtfs-validation-rule.poll-rate}")
    public void handleCanonicalGtfsQueue() {
        listen(MessageQueue.RULE_PROCESSING.munge(RuleName.GTFS_CANONICAL_4_0_0), ValidationRuleJobMessage.class, canonicalGtfsValidatorRule::execute);
    }

    @Scheduled(fixedRateString = "${vaco.scheduling.errors.poll-rate}")
    public void handleErrorsQueue() {
        listen(MessageQueue.ERRORS.getQueueName(), ErrorMessage.class, this::handleErrors);
    }

    private CompletableFuture<Boolean> handleErrors(ErrorMessage errorMessage) {
        return CompletableFuture.supplyAsync(() -> {
            logger.warn("Got ErrorMessage " + errorMessage);
            errorHandlerService.reportErrors(errorMessage.errors());
            return true;
        });
    }

    @Scheduled(fixedRateString = "${vaco.scheduling.rules-results.poll-rate}")
    public void handleRuleResultsIngestQueue() {
        listen(MessageQueue.RULE_RESULTS_INGEST.getQueueName(), ResultMessage.class, this::handleResult);
    }

    private CompletableFuture<Boolean> handleResult(ResultMessage resultMessage) {
        return CompletableFuture.supplyAsync(() -> {
            logger.warn("Got ResultMessage " + resultMessage);
            return switch (resultMessage.ruleName()) {
                case RuleName.NETEX_ENTUR_1_0_1 -> processResultFromNetexEntur101(resultMessage);
                case RuleName.GTFS_CANONICAL_4_0_0 -> processResultFromGtfsCanonical400(resultMessage);
                case RuleName.GTFS_CANONICAL_4_1_0 -> processResultFromGtfsCanonical410(resultMessage);
                default -> {
                    logger.error(
                        "Unexpected rule name detected in queue {}: {}",
                        MessageQueue.RULE_RESULTS_INGEST.getQueueName(),
                        resultMessage.ruleName());
                    yield false;
                }
            };
        });
    }

    private boolean processResultFromNetexEntur101(ResultMessage resultMessage) {
        return false;
    }

    private boolean processResultFromGtfsCanonical400(ResultMessage resultMessage) {
        // this is just an example right now
        return false;
    }

    private boolean processResultFromGtfsCanonical410(ResultMessage resultMessage) {

        Map<String, String> files = Streams.collect(
            resultMessage.uploadedFiles(),
            m -> m.substring(m.lastIndexOf('/') + 1),
            Function.identity());

        Optional<ImmutableEntry> e = queueHandlerService.getEntry(resultMessage.entryId());

        if (e.isPresent()) {
            ImmutableEntry entry = e.get();
            ImmutableTask task = taskService.trackTask(taskService.findTask(entry.id(), resultMessage.ruleName()), ProcessingState.START);

            if (files.containsKey("report.json")) {
                Path ruleTemp = TempFiles.getRuleTempDirectory(vacoProperties, entry, task.name(), resultMessage.ruleName());
                Path outputDir = ruleTemp.resolve("output");

                Path reportFile = outputDir.resolve("report.json");
                URI s3Uri = URI.create(files.get("report.json"));
                s3Client.downloadFile(s3Uri.getHost(), S3Path.of(s3Uri.getPath()), reportFile);

                errorHandlerService.reportErrors(new ArrayList<>(canonicalGtfsValidatorRule.scanReportFile(entry, task, resultMessage.ruleName(), reportFile)));

                packagesService.createPackage(entry, task, resultMessage.ruleName(), S3Path.of(URI.create(resultMessage.outputs()).getPath()), "content.zip");
            } else {
                logger.warn("Expected file 'report.json' missing from output for message {}", resultMessage);
            }
            taskService.trackTask(task, ProcessingState.COMPLETE);
            return true;
        } else {
            logger.error("Unknown entry id in message {}", resultMessage);
            return false;
        }
    }

    private <M, R> void listen(String queueName, Class<M> cls, Function<M, CompletableFuture<R>> function) {
        try {
            List<R> x = messagingService.readMessages(queueName)
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
