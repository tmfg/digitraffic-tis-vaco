package fi.digitraffic.tis.vaco.validation;

import com.google.common.annotations.VisibleForTesting;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.InvalidMappingException;
import fi.digitraffic.tis.vaco.aws.S3Artifact;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.RuleConfiguration;
import fi.digitraffic.tis.vaco.rules.RuleExecutionException;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
import fi.digitraffic.tis.vaco.rules.model.ImmutableValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.rules.model.ValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import fi.digitraffic.tis.vaco.validation.model.RulesetSubmissionConfiguration;
import fi.digitraffic.tis.vaco.validation.model.ValidationJobMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Service
public class RulesetSubmissionService {
    public static final String VALIDATE_TASK = "validate";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TaskService taskService;
    private final S3Client s3Client;
    private final VacoProperties vacoProperties;
    private final MessagingService messagingService;

    // code migration split, these are temporary
    private final RulesetService rulesetService;
    private final PackagesService packagesService;

    public RulesetSubmissionService(TaskService taskService,
                                    S3Client s3Client,
                                    VacoProperties vacoProperties,
                                    MessagingService messagingService,
                                    RulesetService rulesetService,
                                    PackagesService packagesService) {
        this.taskService = Objects.requireNonNull(taskService);
        this.s3Client = Objects.requireNonNull(s3Client);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.messagingService = Objects.requireNonNull(messagingService);
        this.rulesetService = Objects.requireNonNull(rulesetService);
        this.packagesService = packagesService;
    }

    public void validate(ValidationJobMessage message) throws RuleExecutionException {
        Entry entry = message.entry();
        RulesetSubmissionConfiguration configuration = message.configuration();

        taskService.findTask(entry.id(), configuration.submissionTask())
            .map(task -> {
                Set<Ruleset> rulesets = selectRulesets(entry, configuration);

                if (rulesets.isEmpty()) {
                    taskService.markStatus(task, Status.FAILED);
                } else {
                    submitRules(entry, task, configuration, rulesets);
                    taskService.markStatus(task, Status.SUCCESS);
                }
                return taskService.trackTask(task, ProcessingState.COMPLETE);
            }).orElseThrow();
    }

    private Set<Ruleset> selectRulesets(Entry entry, RulesetSubmissionConfiguration configuration) {
        TransitDataFormat format;
        try {
            format = TransitDataFormat.forField(entry.format());
        } catch (InvalidMappingException ime) {
            logger.warn("Cannot select rulesets for entry {}: Unknown format '{}'", entry.publicId(), entry.format(), ime);
            return Set.of();
        }

        // add all task names to rulesets to select to ensure rules from dependencies are also included
        Set<String> rulesetNames = Streams.map(entry.tasks(), Task::name).toSet();

        // find all possible rulesets to execute
        Set<Ruleset> rulesets = Streams.filter(
                rulesetService.selectRulesets(
                    entry.businessId(),
                    configuration.type(),
                    format,
                    rulesetNames),
                // filter to contain only format compatible rulesets
                r -> r.identifyingName().startsWith(entry.format()))
            .toSet();

        logger.info("Selected {} rulesets for {} are {}", configuration.type(), entry.publicId(), Streams.collect(rulesets, Ruleset::identifyingName));

        return rulesets;
    }

    @VisibleForTesting
    void submitRules(Entry entry,
                     Task task,
                     RulesetSubmissionConfiguration configurationx,
                     Set<Ruleset> rulesets) {

        Map<String, RuleConfiguration> configs = new HashMap<>();
        List<ValidationInput> validations = entry.validations();
        if (validations != null) {
            configs.putAll(Streams.filter(validations, v -> v.config() != null)
                .collect(ValidationInput::name, ValidationInput::config));
        }
        List<ConversionInput> conversions = entry.conversions();
        if (conversions != null) {
            configs.putAll(Streams.filter(conversions, v -> v.config() != null)
                .collect(ConversionInput::name, ConversionInput::config));
        }

        Streams.map(rulesets, r -> {
                String identifyingName = r.identifyingName();
                if (rulesetService.dependenciesCompletedSuccessfully(entry, r)) {
                    logger.debug("Entry {}, ruleset {} all dependencies completed successfully, submitting", entry.publicId(), identifyingName);
                    Optional<RuleConfiguration> configuration = Optional.ofNullable(configs.get(identifyingName));
                    ValidationRuleJobMessage ruleMessage = convertToValidationRuleJobMessage(
                        entry,
                        task,
                        configuration,
                        identifyingName);
                    // mark the processing of matching task as started
                    // 1) shows in API response that the processing has started
                    // 2) this prevents unintended retrying of the task
                    Optional<Task> ruleTask = taskService.findTask(entry.id(), identifyingName);
                    ruleTask.map(t -> taskService.trackTask(t, ProcessingState.START))
                        .orElseThrow();
                    return messagingService.submitRuleExecutionJob(identifyingName, ruleMessage);
                } else {
                    logger.warn("Entry {} ruleset {} has failed dependencies, cancelling the matching task", entry.publicId(), identifyingName);
                    // dependencies failed or were cancelled, mark this one as cancelled and complete
                    taskService.findTask(entry.id(), identifyingName)
                        .map(t -> taskService.trackTask(t, ProcessingState.START))
                        .map(t -> taskService.markStatus(t, Status.CANCELLED))
                        .map(t -> taskService.trackTask(t, ProcessingState.COMPLETE))
                        .orElseThrow();
                    return CompletableFuture.completedFuture(null);
                }
            })
            .map(CompletableFuture::join)
            .complete();
    }

    private ValidationRuleJobMessage convertToValidationRuleJobMessage(
        Entry entry,
        Task task,
        Optional<RuleConfiguration> configuration,
        String identifyingName) {
        S3Path ruleBasePath = S3Artifact.getRuleDirectory(entry.publicId(), identifyingName, identifyingName);
        S3Path ruleS3Input = ruleBasePath.resolve("input");
        S3Path ruleS3Output = ruleBasePath.resolve("output");

        copyAllResultsFromCompletedTasks(ruleS3Input, entry, task);

        return ImmutableValidationRuleJobMessage.builder()
            .entry(ImmutableEntry.copyOf(entry).withTasks())
            .task(task)
            .source(task.name())
            .inputs(ruleS3Input.asUri(vacoProperties.s3ProcessingBucket()))
            .outputs(ruleS3Output.asUri(vacoProperties.s3ProcessingBucket()))
            .configuration(configuration.orElse(null))
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
    }

    /**
     * Expose the results of previously completed tasks as inputs to the current task.
     * <p>
     * Transitively this copies downloaded input files and other static content for the task.
     *
     * @param targetDirectory
     * @param entry
     * @see DownloadRule
     * @see fi.digitraffic.tis.vaco.rules.internal.StopsAndQuaysRule
     */
    private void copyAllResultsFromCompletedTasks(S3Path targetDirectory, Entry entry, Task current) {
        List<Task> completedTasks = entry.tasks().stream()
            .filter(task -> task.priority() < current.priority() && task.completed() != null)
            .toList();
        completedTasks.forEach(task -> lookupDownloadedFile(entry, task.name())
            .ifPresent(s3Path -> s3Client.copyFile(vacoProperties.s3ProcessingBucket(), s3Path, targetDirectory).join()));
    }

    private Optional<S3Path> lookupDownloadedFile(Entry entry, String taskName) {
        return taskService.findTask(entry.id(), taskName)
            .flatMap(task -> packagesService.findPackage(task, "result"))
            .map(downloadResult -> S3Path.of(URI.create(downloadResult.path()).getPath()));
    }
}
