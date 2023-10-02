package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.http.HttpClient;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.TempFiles;
import fi.digitraffic.tis.utilities.VisibleForTesting;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.aws.S3Artifact;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.Rule;
import fi.digitraffic.tis.vaco.rules.RuleExecutionException;
import fi.digitraffic.tis.vaco.rules.model.ImmutableValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.rules.model.ValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
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
    public static final String DOWNLOAD_SUBTASK = "validation.download";
    public static final String RULESET_SELECTION_SUBTASK = "validation.rulesets";
    public static final String EXECUTION_SUBTASK = "validation.execute";

    public static final List<String> ALL_SUBTASKS = List.of(DOWNLOAD_SUBTASK, RULESET_SELECTION_SUBTASK, EXECUTION_SUBTASK);

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TaskService taskService;
    private final RulesetService rulesetService;
    private final HttpClient httpClient;
    private final S3Client s3Client;
    private final Map<String, Rule<ValidationInput, ValidationReport>> rules;
    private final VacoProperties vacoProperties;
    private final MessagingService messagingService;

    public ValidationService(TaskService taskService,
                             RulesetService rulesetService,
                             HttpClient httpClient,
                             S3Client s3Client,
                             @Qualifier("validation") List<Rule<ValidationInput, ValidationReport>> rules,
                             VacoProperties vacoProperties,
                             MessagingService messagingService) {
        this.taskService = taskService;
        this.rulesetService = rulesetService;
        this.httpClient = httpClient;
        this.s3Client = s3Client;
        this.rules = Streams.collect(rules, Rule::getIdentifyingName, Function.identity());
        this.vacoProperties = vacoProperties;
        this.messagingService = messagingService;
    }

    public void validate(ValidationJobMessage message) throws RuleExecutionException {
        Entry entry = message.entry();

        S3Path downloadedFile = downloadFile(entry);

        Set<Ruleset> validationRulesets = selectRulesets(entry);

        executeRules(entry, downloadedFile, validationRulesets);
    }

    @VisibleForTesting
    S3Path downloadFile(Entry entry) {
        ImmutableTask task = taskService.trackTask(taskService.findTask(entry.id(), DOWNLOAD_SUBTASK), ProcessingState.START);
        Path tempFilePath = TempFiles.getTaskTempFile(vacoProperties, entry, task, entry.format() + ".zip");

        return httpClient.downloadFile(tempFilePath, entry.url(), entry.etag())
            .thenApply(track(task, ProcessingState.UPDATE))
            .thenCompose(uploadToS3(entry, task))
            .thenApply(track(task, ProcessingState.COMPLETE))
            .join();
    }

    private <T> Function<T, T> track(ImmutableTask task, ProcessingState state) {
        return t -> {
            taskService.trackTask(task, state);
            return t;
        };
    }

    private Function<HttpResponse<Path>, CompletableFuture<S3Path>> uploadToS3(Entry entry,
                                                                               ImmutableTask task) {
        return response -> {
            ImmutableFileReferences refs = ImmutableFileReferences.of(response.body());
            S3Path s3TargetPath = ImmutableS3Path.builder()
                .from(S3Artifact.getTaskPath(entry.publicId(), DOWNLOAD_SUBTASK))
                .addPath(entry.format() + ".zip")
                .build();

            return s3Client.uploadFile(vacoProperties.getS3ProcessingBucket(), s3TargetPath, refs.localPath())
                .thenApply(track(task, ProcessingState.UPDATE))
                .thenApply(u -> s3TargetPath);  // TODO: There's probably something useful in the `u` parameter
        };
    }

    // TODO: convert to CompletableFuture chain
    @VisibleForTesting
    Set<Ruleset> selectRulesets(Entry entry) {
        ImmutableTask task = taskService.trackTask(taskService.findTask(entry.id(), RULESET_SELECTION_SUBTASK), ProcessingState.START);

        Set<Ruleset> rulesets = rulesetService.selectRulesets(
                entry.businessId(),
                Type.VALIDATION_SYNTAX,
                Streams.map(entry.validations(), ValidationInput::name).toSet());

        taskService.trackTask(task, ProcessingState.COMPLETE);

        return rulesets;
    }

    @VisibleForTesting
    void executeRules(Entry entry,
                      S3Path downloadedFile,
                      Set<Ruleset> validationRulesets) {
        ImmutableTask task = taskService.trackTask(taskService.findTask(entry.id(), EXECUTION_SUBTASK), ProcessingState.START);

        Map<String, ValidationInput> configs = Streams.collect(entry.validations(), ValidationInput::name, Function.identity());

        List<ValidationRuleJobMessage> s = validationRulesets.parallelStream()
            .map(r -> {
                String identifyingName = r.identifyingName();
                Optional<ValidationInput> configuration = Optional.ofNullable(configs.get(identifyingName));

                S3Path ruleBasePath = S3Artifact.getRuleDirectory(entry.publicId(), r.identifyingName(), identifyingName);
                S3Path ruleS3Input = ruleBasePath.resolve("input");
                S3Path ruleS3Output = ruleBasePath.resolve("output");

                s3Client.copyFile(vacoProperties.getS3ProcessingBucket(), downloadedFile, ruleS3Input).join();

                ValidationRuleJobMessage ruleMessage = ImmutableValidationRuleJobMessage.builder()
                    .entry(ImmutableEntry.copyOf(entry).withTasks())
                    .task(task)
                    .inputs(ruleS3Input.asUri(vacoProperties.getS3ProcessingBucket()))
                    .outputs(ruleS3Output.asUri(vacoProperties.getS3ProcessingBucket()))
                    .configuration(configuration.orElse(null))
                    .retryStatistics(ImmutableRetryStatistics.of(5))
                    .build();
                return messagingService.submitRuleExecutionJob(identifyingName, ruleMessage);
            })
            .map(CompletableFuture::join)
            .toList();  // this is here to terminate the stream which ensures it gets evaluated properly
        // everything's done at this point because of the ::join call, complete task
        taskService.trackTask(task, ProcessingState.COMPLETE);
    }

}
