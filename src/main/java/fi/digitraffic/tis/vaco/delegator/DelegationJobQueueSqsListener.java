package fi.digitraffic.tis.vaco.delegator;

import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.email.EmailService;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.SqsListenerBase;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
import fi.digitraffic.tis.vaco.rules.internal.StopsAndQuaysRule;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import fi.digitraffic.tis.vaco.validation.RulesetSubmissionService;
import fi.digitraffic.tis.vaco.validation.model.ImmutableRulesetSubmissionConfiguration;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationJobMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
public class DelegationJobQueueSqsListener extends SqsListenerBase<ImmutableDelegationJobMessage> {

    private final MessagingService messagingService;
    private final TaskService taskService;
    private final Set<String> knownExternalRules;
    // internal tasks are direct dependencies
    private final DownloadRule downloadRule;
    private final StopsAndQuaysRule stopsAndQuaysRule;
    private final EmailService emailService;
    private final EntryService entryService;

    public DelegationJobQueueSqsListener(MessagingService messagingService,
                                         TaskService taskService,
                                         RulesetService rulesetService,
                                         DownloadRule downloadRule,
                                         StopsAndQuaysRule stopsAndQuaysRule,
                                         EmailService emailService,
                                         EntryService entryService) {
        super((message, stats) -> messagingService.submitProcessingJob(message.withRetryStatistics(stats)));
        this.messagingService = Objects.requireNonNull(messagingService);
        this.taskService = Objects.requireNonNull(taskService);
        this.downloadRule = Objects.requireNonNull(downloadRule);
        this.knownExternalRules = Objects.requireNonNull(rulesetService).listAllNames();
        this.stopsAndQuaysRule = Objects.requireNonNull(stopsAndQuaysRule);
        this.emailService = Objects.requireNonNull(emailService);
        this.entryService = Objects.requireNonNull(entryService);
    }

    @SqsListener(QueueNames.VACO_JOBS)
    public void listen(ImmutableDelegationJobMessage message, Acknowledgement acknowledgement) {
        handle(message, message.entry().publicId(), acknowledgement, (exhaustedRetries ->
            entryService.markComplete(exhaustedRetries.entry())));
    }

    @Override
    protected void runTask(ImmutableDelegationJobMessage message) {
        Entry entry = message.entry();
        if (entry.started() == null) {
            entryService.markStarted(entry);
        } else {
            entryService.markUpdated(entry);
        }
        List<Task> tasksToRun = nextTaskGroupToRun(message).orElse(List.of());

        logger.info("Entry {} next tasks to run {}", entry.publicId(), tasksToRun);
        if (!tasksToRun.isEmpty()) {
            tasksToRun.forEach(task -> {
                logger.info("Running task {}", task);
                String name = task.name();

                if (name.equals(DownloadRule.DOWNLOAD_SUBTASK)) {
                    logger.debug("Internal rule {} detected, delegating...", name);
                    messagingService.sendMessage(QueueNames.VACO_RULES_RESULTS, downloadRule.execute(entry).join());
                } else if (name.equals(StopsAndQuaysRule.STOPS_AND_QUAYS_TASK)) {
                    logger.debug("Internal rule {} detected, delegating...", name);
                    messagingService.sendMessage(QueueNames.VACO_RULES_RESULTS, stopsAndQuaysRule.execute(entry).join());
                } else if (name.equals(RulesetSubmissionService.VALIDATE_TASK)) {
                    logger.debug("Internal category {} detected, delegating...", name);
                    taskService.findTask(entry.publicId(), name)
                        .ifPresent(t -> {
                            taskService.trackTask(entry, t, ProcessingState.START);
                            validationJob(entry);
                            taskService.markStatus(entry, t, Status.SUCCESS);
                        });
                } else if (name.equals(RulesetSubmissionService.CONVERT_TASK)) {
                    logger.debug("Internal category {} detected, delegating...", name);
                    taskService.findTask(entry.publicId(), name)
                        .ifPresent(t -> {
                            taskService.trackTask(entry, t, ProcessingState.START);
                            conversionJob(entry);
                            taskService.markStatus(entry, t, Status.SUCCESS);
                        });
                } else if (knownExternalRules.contains(name)) {
                    // these are currently submitted delegatively elsewhere
                    logger.debug("External rule {} detected, skipping...", name);
                    taskService.trackTask(entry, task, ProcessingState.START);
                } else {
                    logger.info("Unknown task, marking it as complete to avoid infinite looping {} / {}", task, message);
                    taskService.trackTask(entry, task, ProcessingState.START);
                    taskService.trackTask(entry, task, ProcessingState.COMPLETE);
                    taskService.markStatus(entry, task, Status.CANCELLED);
                }
            });
        } else {
            if (taskService.areAllTasksCompleted(entry)) {
                logger.debug("Job for entry {} complete!", entry.publicId());
                entryService.markComplete(entry);
                entryService.updateStatus(entry);
                emailService.notifyEntryComplete(entry);
            } else {
                // some kind of limbo/crash
            }
        }
    }

    private void validationJob(Entry entry) {
        ImmutableValidationJobMessage validationJob = ImmutableValidationJobMessage.builder()
            .entry(entry)
            .configuration(ImmutableRulesetSubmissionConfiguration
                .builder()
                .submissionTask(RulesetSubmissionService.VALIDATE_TASK)
                .type(Type.VALIDATION_SYNTAX)
                .build())
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
        messagingService.submitValidationJob(validationJob);
    }

    private void conversionJob(Entry entry) {
        ImmutableValidationJobMessage validationJob = ImmutableValidationJobMessage.builder()
            .entry(entry)
            .configuration(ImmutableRulesetSubmissionConfiguration
                .builder()
                .submissionTask(RulesetSubmissionService.CONVERT_TASK)
                .type(Type.CONVERSION_SYNTAX)
                .build())
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
        messagingService.submitValidationJob(validationJob);
    }

    private Optional<List<Task>> nextTaskGroupToRun(ImmutableDelegationJobMessage jobDescription) {
        List<Task> availableForExecuting = taskService.findTasksToExecute(jobDescription.entry());

        return availableForExecuting.isEmpty()
            ? Optional.empty()
            : Optional.of(availableForExecuting);
    }
}
