package fi.digitraffic.tis.vaco.validation;

import com.google.common.annotations.VisibleForTesting;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.aws.S3Artifact;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.DelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
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

@Service
public class RulesetSubmissionService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TaskService taskService;
    private final S3Client s3Client;
    private final VacoProperties vacoProperties;
    private final MessagingService messagingService;

    // code migration split, these are temporary
    private final RulesetService rulesetService;
    private final PackagesService packagesService;
    private final QueueHandlerService queueHandlerService;

    public RulesetSubmissionService(TaskService taskService,
                                    S3Client s3Client,
                                    VacoProperties vacoProperties,
                                    MessagingService messagingService,
                                    RulesetService rulesetService,
                                    PackagesService packagesService,
                                    QueueHandlerService queueHandlerService) {
        this.taskService = Objects.requireNonNull(taskService);
        this.s3Client = Objects.requireNonNull(s3Client);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.messagingService = Objects.requireNonNull(messagingService);
        this.rulesetService = Objects.requireNonNull(rulesetService);
        this.packagesService = Objects.requireNonNull(packagesService);
        this.queueHandlerService = Objects.requireNonNull(queueHandlerService);
    }

    public void submit(ValidationJobMessage message) throws RuleExecutionException {
        Entry entry = message.entry();
        RulesetSubmissionConfiguration configuration = message.configuration();

        taskService.findTask(configuration.taskPublicId())
            .ifPresent(task -> {
                Optional<Ruleset> ruleset = selectRuleset(entry, task);

                if (ruleset.isPresent()) {
                    submitTask(entry, task, ruleset.get());
                } else {
                    taskService.markStatus(entry, task, Status.FAILED);
                }
            });
    }

    private Optional<Ruleset> selectRuleset(Entry entry, Task task) {
        Optional<Ruleset> ruleset = rulesetService.findByName(task.name());

        if (ruleset.isPresent()) {
            Ruleset rs = ruleset.get();
            // TODO: this is probably not needed, but its intent is to block access to unauthorized rules
            Set<Ruleset> allowedRulesets = rulesetService.selectRulesets(
                entry.businessId(),
                rs.type(),
                rs.format(),
                Set.of(task.name())
            );
            if (allowedRulesets.contains(rs)) {
                return Optional.of(rs);
            }
        }
        return Optional.empty();
    }

    @VisibleForTesting
    void submitTask(Entry entry,
                    Task task,
                    Ruleset r) {

        Map<String, RuleConfiguration> userProvidedConfigs = new HashMap<>();
        List<ValidationInput> validations = entry.validations();
        if (validations != null) {
            userProvidedConfigs.putAll(Streams.filter(validations, v -> v.config() != null)
                .collect(ValidationInput::name, ValidationInput::config));
        }
        List<ConversionInput> conversions = entry.conversions();
        if (conversions != null) {
            userProvidedConfigs.putAll(Streams.filter(conversions, v -> v.config() != null)
                .collect(ConversionInput::name, ConversionInput::config));
        }

        String identifyingName = r.identifyingName();

        // mark the processing of matching task as started
        Optional<Task> ruleTask = taskService.findTask(entry.publicId(), identifyingName);
        ruleTask.map(t -> taskService.trackTask(entry, t, ProcessingState.START))
            .orElseThrow();

        // TODO: Should ensure the previous deps are ok, not all globally. This case trips only if the same flow has same rule multiple times, which is unlikely.
        if (rulesetService.dependenciesCompletedSuccessfully(entry, r)) {
            logger.debug("Entry {}, ruleset {} all dependencies completed successfully, submitting", entry.publicId(), identifyingName);
            // mark the processing of matching task as started
            submit(entry, task, identifyingName, userProvidedConfigs);
        } else {
            if (rulesetService.dependenciesProcessing(entry, r)) {
                logger.debug("Entry {}, ruleset {} some dependencies still processing, submitting", entry.publicId(), identifyingName);
                delay(entry);
            } else {
                logger.warn("Entry {} ruleset {} has failed dependencies, cancelling the matching task", entry.publicId(), identifyingName);
                cancel(entry, task, identifyingName, r);
            }
        }
    }

    private void cancel(Entry entry, Task task, String identifyingName, Ruleset r) {
        // dependencies failed or were cancelled, mark this one as cancelled and complete
        taskService.findTask(entry.publicId(), identifyingName)
            .map(t -> taskService.trackTask(entry, t, ProcessingState.START))
            .map(t -> taskService.markStatus(entry, t, Status.CANCELLED))
            .map(t -> taskService.trackTask(entry, t, ProcessingState.COMPLETE))
            .orElseThrow();

        r.afterDependencies().forEach( dependency -> {
            taskService.findTask(entry.publicId(), dependency)
                .filter(depTask -> depTask.priority() > task.priority()) //avoid cancelling potentially wrong tasks
                .map(t -> taskService.trackTask(entry, t, ProcessingState.START))
                .map(t -> taskService.markStatus(entry, t, Status.CANCELLED))
                .map(t -> taskService.trackTask(entry, t, ProcessingState.COMPLETE))
                .orElse(null);
        });

        DelegationJobMessage message = ImmutableDelegationJobMessage.builder()
            .entry(queueHandlerService.getEntry(entry.publicId()))
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
        messagingService.submitProcessingJob(message).join();
    }

    private void submit(Entry entry, Task task, String identifyingName, Map<String, RuleConfiguration> userProvidedConfigs) {

        Optional<RuleConfiguration> configuration = Optional.ofNullable(userProvidedConfigs.get(identifyingName));
        ValidationRuleJobMessage ruleMessage = convertToValidationRuleJobMessage(
            entry,
            task,
            configuration,
            identifyingName);
        messagingService.submitRuleExecutionJob(identifyingName, ruleMessage).join();
    }


    private void delay(Entry entry) {
        DelegationJobMessage delegationJobMessage = convertoToDelegationJobMessage(
            entry
        );
        messagingService.submitProcessingJob(delegationJobMessage).join();
    }

    private DelegationJobMessage convertoToDelegationJobMessage(Entry entry) {
        return ImmutableDelegationJobMessage.builder()
            .entry(entry)
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
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
     * <p>
     * The copying logic has two branches due to legacy reasons, one which puts all outputs as is to root of provided
     * directory and another which categorizes the outputs of previous tasks by name. The latter is the one which should
     * be used in longterm.
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
            .ifPresent(s3Path -> {
                // legacy logic: copy all results as is
                S3Path targetPath = targetDirectory.resolve(s3Path.path().getLast());
                s3Client.copyFile(vacoProperties.s3ProcessingBucket(), s3Path, targetPath).join();
                // new logic: categorize outputs by task name (could be publicId?)
                S3Path targetTaskPath = targetDirectory.resolve(task.name()).resolve(s3Path.path().getLast());
                s3Client.copyFile(vacoProperties.s3ProcessingBucket(), s3Path, targetTaskPath).join();
            }));
    }

    private Optional<S3Path> lookupDownloadedFile(Entry entry, String taskName) {
        return taskService.findTask(entry.publicId(), taskName)
            .flatMap(task -> packagesService.findPackage(task, "result"))
            .map(downloadResult -> S3Path.of(URI.create(downloadResult.path()).getPath()));
    }
}
