package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.VisibleForTesting;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.InvalidMappingException;
import fi.digitraffic.tis.vaco.aws.S3Artifact;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.RuleExecutionException;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
import fi.digitraffic.tis.vaco.rules.model.ImmutableValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.rules.model.ValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import fi.digitraffic.tis.vaco.validation.model.ValidationJobMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Service
public class ValidationService {
    public static final String VALIDATE_TASK = "validate";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TaskService taskService;
    private final S3Client s3Client;
    private final VacoProperties vacoProperties;
    private final MessagingService messagingService;

    // code migration split, these are temporary
    private final DownloadRule downloadRule;
    private final RulesetService rulesetService;
    private final PackagesService packagesService;

    public ValidationService(TaskService taskService,
                             S3Client s3Client,
                             VacoProperties vacoProperties,
                             MessagingService messagingService,
                             DownloadRule downloadRule,
                             RulesetService rulesetService, PackagesService packagesService) {
        this.taskService = Objects.requireNonNull(taskService);
        this.s3Client = Objects.requireNonNull(s3Client);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.messagingService = Objects.requireNonNull(messagingService);
        this.downloadRule = Objects.requireNonNull(downloadRule);
        this.rulesetService = Objects.requireNonNull(rulesetService);
        this.packagesService = packagesService;
    }

    public void validate(ValidationJobMessage message) throws RuleExecutionException {
        Entry entry = message.entry();

        Task task = taskService.trackTask(taskService.findTask(entry.id(), ValidationService.VALIDATE_TASK), ProcessingState.START);

        S3Path downloadedFile = lookupDownloadedFile(entry);

        Set<Ruleset> validationRulesets = selectRulesets(entry);

        executeRules(entry, task, downloadedFile, validationRulesets);

        taskService.trackTask(task, ProcessingState.COMPLETE);
    }

    private S3Path lookupDownloadedFile(Entry entry) {
        Task downloadTask = taskService.findTask(entry.id(), DownloadRule.DOWNLOAD_SUBTASK);
        Package downloadResult = packagesService.findPackage(downloadTask, "result")
            .orElseThrow(() -> new RuleExecutionException("No download.rule/result package available for entry " + entry.publicId()));
        return S3Path.of(URI.create(downloadResult.path()).getPath());
    }

    private Set<Ruleset> selectRulesets(Entry entry) {
        TransitDataFormat format;
        try {
            format = TransitDataFormat.forField(entry.format());
        } catch (InvalidMappingException ime) {
            logger.warn("Cannot select rulesets for entry {}: Unknown format '{}'", entry.publicId(), entry.format(), ime);
            return Set.of();
        }

        // find all possible rulesets to execute
        Set<Ruleset> rulesets = Streams.filter(
                rulesetService.selectRulesets(
                    entry.businessId(),
                    Type.VALIDATION_SYNTAX,
                    format,
                    Streams.map(entry.validations(), ValidationInput::name).toSet()),
                // filter to contain only format compatible rulesets
                r -> r.identifyingName().startsWith(entry.format() + "."))
            .toSet();

        logger.info("Selected rulesets for {} are {}", entry.publicId(), Streams.collect(rulesets, Ruleset::identifyingName));

        return rulesets;
    }

    @VisibleForTesting
    void executeRules(Entry entry,
                      Task task,
                      S3Path downloadedFile,
                      Set<Ruleset> validationRulesets) {
        Map<String, ValidationInput> configs = Streams.collect(entry.validations(), ValidationInput::name, Function.identity());

        Streams.map(validationRulesets, r -> {
                String identifyingName = r.identifyingName();
                Optional<ValidationInput> configuration = Optional.ofNullable(configs.get(identifyingName));
                ValidationRuleJobMessage ruleMessage = convertToValidationRuleJobMessage(
                    entry,
                    task,
                    downloadedFile,
                    configuration,
                    identifyingName);
                // mark the processing of matching task as started
                // 1) shows in API response that the processing has started
                // 2) this prevents unintended retrying of the task
                taskService.trackTask(taskService.findTask(entry.id(), identifyingName), ProcessingState.START);
                return messagingService.submitRuleExecutionJob(identifyingName, ruleMessage);
            })
            .map(CompletableFuture::join)
            .complete();
    }

    private ValidationRuleJobMessage convertToValidationRuleJobMessage(
        Entry entry,
        Task task,
        S3Path downloadedFile,
        Optional<ValidationInput> configuration,
        String identifyingName) {
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
