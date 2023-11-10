package fi.digitraffic.tis.vaco.delegator;

import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.conversion.ConversionService;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.SqsListenerBase;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
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
    private final DownloadRule downloadRule;
    private final Set<String> knownExternalRules;

    public DelegationJobQueueSqsListener(MessagingService messagingService,
                                         TaskService taskService,
                                         DownloadRule downloadRule,
                                         RulesetService rulesetService) {
        super((message, stats) -> messagingService.submitProcessingJob(message.withRetryStatistics(stats)));
        this.messagingService = Objects.requireNonNull(messagingService);
        this.taskService = Objects.requireNonNull(taskService);
        this.downloadRule = Objects.requireNonNull(downloadRule);
        this.knownExternalRules = Objects.requireNonNull(rulesetService).listAllNames();
    }

    @SqsListener(QueueNames.VACO_JOBS)
    public void listen(ImmutableDelegationJobMessage message, Acknowledgement acknowledgement) {
        handle(message, message.entry().publicId(), acknowledgement, (exhaustedRetries ->
            messagingService.updateJobProcessingStatus(exhaustedRetries, ProcessingState.COMPLETE)));
    }

    @Override
    protected void runTask(ImmutableDelegationJobMessage message) {
        Entry entry = message.entry();
        List<Task> tasksToRun = nextTaskGroupToRun(message).orElse(List.of());

        logger.info("Entry {} next tasks to run {}", entry.publicId(), tasksToRun);

        if (!tasksToRun.isEmpty()) {
            tasksToRun.forEach(task -> {
                logger.info("Running task {}", task);
                String name = task.name();

                if (name.equals(DownloadRule.DOWNLOAD_SUBTASK)) {
                    logger.debug("Internal rule {} detected, delegating...", name);
                    messagingService.sendMessage(QueueNames.VACO_RULES_RESULTS, downloadRule.execute(entry).join());
                } else if (name.equals(RulesetSubmissionService.VALIDATE_TASK)) {
                    logger.debug("Internal category {} detected, delegating...", name);
                    taskService.findTask(entry.id(), name)
                        .ifPresent(t -> {
                            taskService.trackTask(t, ProcessingState.START);
                            validationJob(entry);
                        });
                } else if (name.equals(ConversionService.CONVERT_TASK)) {
                    logger.debug("Internal category {} detected, delegating...", name);
                    taskService.findTask(entry.id(), name)
                        .ifPresent(t -> {
                            taskService.trackTask(t, ProcessingState.START);
                            conversionJob(entry);
                        });
                } else if (knownExternalRules.contains(name)) {
                    // these are currently submitted delegatively elsewhere
                    logger.debug("External rule {} detected, skipping...", name);
                    taskService.trackTask(task, ProcessingState.START);
                } else {
                    logger.info("Unknown task, marking it as complete to avoid infinite looping {} / {}", task, message);
                    // TODO: we could have explicit canceling detection+handling as well
                    taskService.trackTask(task, ProcessingState.START);
                    taskService.trackTask(task, ProcessingState.COMPLETE);
                }
            });
        } else {
            // nothing to run, we're done

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
                .submissionTask(ConversionService.CONVERT_TASK)
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
