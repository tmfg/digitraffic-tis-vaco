package fi.digitraffic.tis.vaco.rules;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.TempFiles;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiPredicate;
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
                               VacoProperties vacoProperties,
                               QueueHandlerService queueHandlerService,
                               PackagesService packagesService,
                               TaskService taskService) {
        this.messagingService = Objects.requireNonNull(messagingService);
        this.errorHandlerService = Objects.requireNonNull(errorHandlerService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.s3Client = Objects.requireNonNull(s3Client);
        this.canonicalGtfsValidatorRule = Objects.requireNonNull(canonicalGtfsValidatorRule);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.queueHandlerService = Objects.requireNonNull(queueHandlerService);
        this.packagesService = Objects.requireNonNull(packagesService);
        this.taskService = Objects.requireNonNull(taskService);
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
            logger.warn("Got ErrorMessage {}", errorMessage);
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
            logger.warn("Got ResultMessage {}", resultMessage);
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
        return processExternalRule(resultMessage, (entry, task) -> {
            createOutputPackages(resultMessage, entry, task);
            return true;
        });
    }

    private boolean processResultFromGtfsCanonical400(ResultMessage resultMessage) {
        // there's no difference in processing between Canonical GTFS Validator's point releases
        return processResultFromGtfsCanonical410(resultMessage);
    }

    private boolean processResultFromGtfsCanonical410(ResultMessage resultMessage) {
        return processExternalRule(resultMessage, (entry, task) -> {
            createOutputPackages(resultMessage, entry, task);

            Map<String, String> fileNames = collectOutputFileNames(resultMessage);

            // file specific handling
            if (fileNames.containsKey("report.json")) {
                Path ruleTemp = TempFiles.getRuleTempDirectory(vacoProperties, entry, task.name(), resultMessage.ruleName());
                Path outputDir = ruleTemp.resolve("output");

                Path reportFile = outputDir.resolve("report.json");
                URI s3Uri = URI.create(fileNames.get("report.json"));
                s3Client.downloadFile(s3Uri.getHost(), S3Path.of(s3Uri.getPath()), reportFile);

                errorHandlerService.reportErrors(new ArrayList<>(canonicalGtfsValidatorRule.scanReportFile(entry, task, resultMessage.ruleName(), reportFile)));
            } else {
                logger.warn("Expected file 'report.json' missing from output for message {}", resultMessage);
            }
            return true;
        });
    }

    private boolean processExternalRule(ResultMessage resultMessage, BiPredicate<Entry, Task> processingHandler) {
        Optional<Entry> e = queueHandlerService.getEntry(resultMessage.entryId());
        if (e.isPresent()) {
            Entry entry = e.get();
            Task task = taskService.trackTask(taskService.findTask(entry.id(), resultMessage.ruleName()), ProcessingState.UPDATE);
            try {
                return processingHandler.test(entry, task);
            } finally {
                taskService.trackTask(task, ProcessingState.COMPLETE);
            }
        } else {
            return false;
        }
    }

    /**
     * Truncate output filenames to contain only the file name without path. Assumes results are in flat directory.
     *
     * @param resultMessage Message to get the file names from.
     * @return Map of file names without directory prefixes to full file name.
     */
    private static Map<String, String> collectOutputFileNames(ResultMessage resultMessage) {
        return Streams.collect(
            resultMessage.uploadedFiles().keySet(),
            m -> m.substring(m.lastIndexOf('/') + 1),
            Function.identity());
    }

    private void createOutputPackages(ResultMessage resultMessage, Entry entry, Task task) {
        // package generation based on rule outputs
        ConcurrentMap<String, List<String>> packagesToCreate = new ConcurrentHashMap<>();
        resultMessage.uploadedFiles().forEach((file, packages) -> {
            for (String p : packages) {
                packagesToCreate.computeIfAbsent(p, k -> new ArrayList<>()).add(file);
            }
        });

        packagesToCreate.forEach((packageName, files) -> {
            logger.info("Creating package '{}' with files {}", packageName, files);
            packagesService.createPackage(
                entry,
                task,
                packageName,
                S3Path.of(URI.create(resultMessage.outputs()).getPath()), packageName + ".zip",
                p -> files.stream().anyMatch(p::endsWith));
        });
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
