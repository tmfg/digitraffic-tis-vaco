package fi.digitraffic.tis.vaco.delegator;

import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.SqsListenerBase;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
import fi.digitraffic.tis.vaco.validation.ValidationService;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationJobMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class DelegationJobQueueSqsListener extends SqsListenerBase<ImmutableDelegationJobMessage> {

    private final MessagingService messagingService;
    private final TaskService taskService;

    public DelegationJobQueueSqsListener(MessagingService messagingService,
                                         TaskService taskService) {
        super((message, stats) -> messagingService.submitProcessingJob(message.withRetryStatistics(stats)));
        this.messagingService = Objects.requireNonNull(messagingService);
        this.taskService = Objects.requireNonNull(taskService);
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
                // is rule internal?
                if (name.equals(DownloadRule.DOWNLOAD_SUBTASK)
                    || name.equals(ValidationService.RULESET_SELECTION_SUBTASK)
                    || name.equals(ValidationService.EXECUTION_SUBTASK)) {
                    validationJob(entry);
                } else {
                    logger.info("Unknown task {}", message);
                }
            });
        }
    }

    private void validationJob(Entry entry) {
        ImmutableValidationJobMessage validationJob = ImmutableValidationJobMessage.builder()
            .entry(entry)
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
