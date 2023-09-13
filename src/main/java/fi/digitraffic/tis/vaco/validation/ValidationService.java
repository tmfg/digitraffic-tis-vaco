package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.http.HttpClient;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.VisibleForTesting;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.aws.S3Artifact;
import fi.digitraffic.tis.vaco.delegator.model.TaskCategory;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.ImmutableJobResult;
import fi.digitraffic.tis.vaco.process.model.ImmutableTaskData;
import fi.digitraffic.tis.vaco.process.model.ImmutableTaskResult;
import fi.digitraffic.tis.vaco.process.model.JobResult;
import fi.digitraffic.tis.vaco.process.model.TaskData;
import fi.digitraffic.tis.vaco.process.model.TaskResult;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.Rule;
import fi.digitraffic.tis.vaco.rules.RuleExecutionException;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import fi.digitraffic.tis.vaco.validation.model.FileReferences;
import fi.digitraffic.tis.vaco.validation.model.ImmutableFileReferences;
import fi.digitraffic.tis.vaco.validation.model.ValidationJobMessage;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Service
public class ValidationService {
    public static final String TASK = TaskCategory.VALIDATION.name;
    public static final String DOWNLOAD_SUBTASK = "validation.download";
    public static final String RULESET_SELECTION_SUBTASK = "validation.rulesets";
    public static final String EXECUTION_SUBTASK = "validation.execute";

    public static final List<String> ALL_SUBTASKS = List.of(DOWNLOAD_SUBTASK, RULESET_SELECTION_SUBTASK, EXECUTION_SUBTASK);

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TaskService taskService;
    private final RulesetService rulesetService;
    private final HttpClient httpClientUtility;
    private final S3Client s3ClientUtility;
    private final Map<String, Rule<ValidationInput, ValidationReport>> rules;

    private final VacoProperties vacoProperties;

    public ValidationService(TaskService taskService,
                             RulesetService rulesetService,
                             HttpClient httpClient,
                             S3Client s3ClientUtility,
                             @Qualifier("validation") List<Rule<ValidationInput, ValidationReport>> rules,
                             VacoProperties vacoProperties) {
        this.taskService = taskService;
        this.rulesetService = rulesetService;
        this.httpClientUtility = httpClient;
        this.s3ClientUtility = s3ClientUtility;
        this.rules = Streams.collect(rules, Rule::getIdentifyingName, Function.identity());
        this.vacoProperties = vacoProperties;
    }

    public JobResult validate(ValidationJobMessage jobMessage) throws RuleExecutionException {
        Entry entry = jobMessage.message();
        TaskResult<ImmutableFileReferences> s3path = downloadFile(entry);

        TaskResult<Set<Ruleset>> validationRulesets = selectRulesets(entry);

        TaskResult<List<ValidationReport>> validationReports = executeRules(entry, s3path.result(), validationRulesets.result());

        return ImmutableJobResult.builder()
                .addResults(s3path, validationRulesets, validationReports)
                .build();
    }

    @VisibleForTesting
    TaskResult<ImmutableFileReferences> downloadFile(Entry queueEntry) {
        ImmutableTaskData<ImmutableFileReferences> taskData = ImmutableTaskData.of(
                taskService.trackTask(taskService.findTask(queueEntry.id(), DOWNLOAD_SUBTASK), ProcessingState.START));
        Path tempFilePath = s3ClientUtility.createVacoDownloadTempFile(queueEntry.publicId(),
            queueEntry.format(), taskData.task().name());

        return httpClientUtility.downloadFile(tempFilePath, queueEntry.url(), queueEntry.etag())
            .thenApply(wrapHttpResult(taskData))
            .thenCompose(uploadToS3(queueEntry))
            .thenApply(completeDownloadTask())
            .join(); // wait for finish - might be temporary
    }

    private Function<HttpResponse<Path>, ImmutableTaskData<ImmutableFileReferences>> wrapHttpResult(ImmutableTaskData<ImmutableFileReferences> taskData) {
        return path -> taskData
                // download done -> update task
                .withTask(taskService.trackTask(taskData.task(), ProcessingState.UPDATE))
                .withPayload(ImmutableFileReferences.of(path.body()));
    }

    private Function<ImmutableTaskData<ImmutableFileReferences>, CompletableFuture<ImmutableTaskData<ImmutableFileReferences>>> uploadToS3(Entry queueEntry) {
        return taskData -> {
            String s3TargetPath = S3Artifact.getTaskPath(queueEntry.publicId(), DOWNLOAD_SUBTASK) + "/" + queueEntry.format() + ".original";
            Path sourcePath = taskData.payload().localPath();

            return s3ClientUtility.uploadFile(vacoProperties.getS3ProcessingBucket(), s3TargetPath, sourcePath)
                .thenApply(u -> taskData // upload done -> update task
                    .withTask(taskService.trackTask(taskData.task(), ProcessingState.UPDATE)));
        };
    }

    private Function<TaskData<ImmutableFileReferences>, TaskResult<ImmutableFileReferences>> completeDownloadTask() {
        return taskData -> {
            ImmutableFileReferences fileRefs = taskData.payload();
            logger.info("S3 path: {}, upload status: {}", fileRefs.s3Path(), fileRefs.upload());
            // download complete, mark to database as complete and unwrap payload
            taskService.trackTask(taskData.task(), ProcessingState.COMPLETE);
            return ImmutableTaskResult.of(DOWNLOAD_SUBTASK, fileRefs);
        };
    }

    @VisibleForTesting
    TaskResult<Set<Ruleset>> selectRulesets(Entry entry) {
        ImmutableTaskData<Ruleset> taskData = ImmutableTaskData.of(
                taskService.trackTask(taskService.findTask(entry.id(), RULESET_SELECTION_SUBTASK), ProcessingState.START));

        Set<Ruleset> rulesets = rulesetService.selectRulesets(
                entry.businessId(),
                Type.VALIDATION_SYNTAX,
                Streams.map(entry.validations(), ValidationInput::name).toSet());

        taskData.withTask(taskService.trackTask(taskData.task(), ProcessingState.COMPLETE));

        return ImmutableTaskResult.of(RULESET_SELECTION_SUBTASK, rulesets);
    }

    @VisibleForTesting
    ImmutableTaskResult<List<ValidationReport>> executeRules(Entry entry, ImmutableFileReferences fileReferences, Set<Ruleset> validationRulesets) {
        TaskData<FileReferences> taskData = ImmutableTaskData.<FileReferences>builder()
                .task(taskService.trackTask(taskService.findTask(entry.id(), EXECUTION_SUBTASK), ProcessingState.START))
                .payload(fileReferences)
                .build();

        Map<String, ValidationInput> configs = Streams.collect(entry.validations(), ValidationInput::name, Function.identity());

        List<ValidationReport> reports = validationRulesets.parallelStream()
                .map(this::findMatchingRule)
                .filter(Optional::isPresent)
                .map(r -> r.get().execute(entry, r.map(x -> configs.get(x.getIdentifyingName())), taskData))
                .map(CompletableFuture::join)
                .toList();
        // everything's done at this point because of the ::join call, complete task and return
        taskService.trackTask(taskData.task(), ProcessingState.COMPLETE);
        return ImmutableTaskResult.of(EXECUTION_SUBTASK, reports);
    }

    private Optional<Rule<ValidationInput, ValidationReport>> findMatchingRule(Ruleset validationRule) {
        String identifyingName = validationRule.identifyingName();
        Optional<Rule<ValidationInput, ValidationReport>> rule = Optional.ofNullable(rules.get(identifyingName));
        if (rule.isEmpty()) {
            logger.error("No matching rule found with identifying name '{}' from available {}", identifyingName, rules.keySet());
        }
        return rule;
    }

    public List<String> listSubTasks() {
        return ALL_SUBTASKS;
    }
}
