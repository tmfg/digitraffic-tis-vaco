package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.VisibleForTesting;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.aws.S3Artifact;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.RuleExecutionException;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
import fi.digitraffic.tis.vaco.rules.internal.SelectRulesetsRule;
import fi.digitraffic.tis.vaco.rules.model.ImmutableValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.rules.model.ValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.validation.model.ValidationJobMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Service
public class ValidationService {
    public static final String DOWNLOAD_SUBTASK = "validation.download";
    public static final String RULESET_SELECTION_SUBTASK = "validation.rulesets";
    public static final String EXECUTION_SUBTASK = "validation.execute";

    public static final List<String> ALL_SUBTASKS = List.of(DOWNLOAD_SUBTASK, RULESET_SELECTION_SUBTASK, EXECUTION_SUBTASK);

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TaskService taskService;
    private final S3Client s3Client;
    private final VacoProperties vacoProperties;
    private final MessagingService messagingService;

    // code migration split, these are temporary
    private final DownloadRule downloadRule;
    private final SelectRulesetsRule selectRulesetsRule;

    public ValidationService(TaskService taskService,
                             S3Client s3Client,
                             VacoProperties vacoProperties,
                             MessagingService messagingService,
                             DownloadRule downloadRule,
                             SelectRulesetsRule selectRulesetsRule) {
        this.taskService = Objects.requireNonNull(taskService);
        this.s3Client = Objects.requireNonNull(s3Client);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.messagingService = Objects.requireNonNull(messagingService);
        this.downloadRule = Objects.requireNonNull(downloadRule);
        this.selectRulesetsRule = Objects.requireNonNull(selectRulesetsRule);
    }

    public void validate(ValidationJobMessage message) throws RuleExecutionException {
        Entry entry = message.entry();

        S3Path downloadedFile = downloadRule.execute(entry).join();

        Set<Ruleset> validationRulesets = selectRulesetsRule.execute(entry).join();

        executeRules(entry, downloadedFile, validationRulesets);
    }

    @VisibleForTesting
    void executeRules(Entry entry,
                      S3Path downloadedFile,
                      Set<Ruleset> validationRulesets) {
        Task task = taskService.trackTask(taskService.findTask(entry.id(), EXECUTION_SUBTASK), ProcessingState.START);

        Map<String, ValidationInput> configs = Streams.collect(entry.validations(), ValidationInput::name, Function.identity());

        Streams.map(validationRulesets, r -> {
                String identifyingName = r.identifyingName();
                Optional<ValidationInput> configuration = Optional.ofNullable(configs.get(identifyingName));
                ValidationRuleJobMessage ruleMessage = convertToValidationRuleJobMessage(
                    entry,
                    downloadedFile,
                    configuration,
                    identifyingName,
                    task);
                // mark the processing of matching task as started
                // 1) shows in API response that the processing has started
                // 2) this prevents unintended retrying of the task
                taskService.trackTask(taskService.findTask(entry.id(), identifyingName), ProcessingState.START);
                return messagingService.submitRuleExecutionJob(identifyingName, ruleMessage);
            })
            .map(CompletableFuture::join)
            .complete();
        // everything's done at this point because of the ::join call, complete task
        taskService.trackTask(task, ProcessingState.COMPLETE);
    }

    private ValidationRuleJobMessage convertToValidationRuleJobMessage(
        Entry entry,
        S3Path downloadedFile,
        Optional<ValidationInput> configuration,
        String identifyingName,
        Task task) {
        S3Path ruleBasePath = S3Artifact.getRuleDirectory(entry.publicId(), identifyingName, identifyingName);
        S3Path ruleS3Input = ruleBasePath.resolve("input");
        S3Path ruleS3Output = ruleBasePath.resolve("output");

        s3Client.copyFile(vacoProperties.s3ProcessingBucket(), downloadedFile, ruleS3Input).join();

        return ImmutableValidationRuleJobMessage.builder()
            .entry(ImmutableEntry.copyOf(entry).withTasks())
            .task(task)
            .inputs(ruleS3Input.asUri(vacoProperties.s3ProcessingBucket()))
            .outputs(ruleS3Output.asUri(vacoProperties.s3ProcessingBucket()))
            .configuration(configuration.orElse(null))
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
    }

}
