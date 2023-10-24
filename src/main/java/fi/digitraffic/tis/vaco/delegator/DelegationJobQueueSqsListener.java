package fi.digitraffic.tis.vaco.delegator;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.VisibleForTesting;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.conversion.model.ImmutableConversionJobMessage;
import fi.digitraffic.tis.vaco.delegator.model.TaskCategory;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.SqsListenerBase;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
import fi.digitraffic.tis.vaco.process.TaskRepository;
import fi.digitraffic.tis.vaco.process.model.Task;
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

    private static final TaskCategory DEFAULT_TASK_CATEGORY = TaskCategory.VALIDATION;

    private final MessagingService messagingService;
    private final TaskRepository taskRepository;

    public DelegationJobQueueSqsListener(MessagingService messagingService,
                                         TaskRepository taskRepository) {
        super((message, stats) -> messagingService.submitProcessingJob(message.withRetryStatistics(stats)));
        this.messagingService = messagingService;
        this.taskRepository = taskRepository;
    }

    @SqsListener(QueueNames.VACO_JOBS)
    public void listen(ImmutableDelegationJobMessage message, Acknowledgement acknowledgement) {
        handle(message, message.entry().publicId(), acknowledgement, (exhaustedRetries ->
            messagingService.updateJobProcessingStatus(exhaustedRetries, ProcessingState.COMPLETE)));
    }

    @Override
    protected void runTask(ImmutableDelegationJobMessage message) {
        Optional<TaskCategory> taskToRun = nextSubtaskToRun(message);

        if (taskToRun.isPresent()) {
            logger.info("Job for entry {} next task to run {}", message.entry().publicId(), taskToRun.get());

            TaskCategory taskCategory = taskToRun.get();
            if (taskCategory == TaskCategory.VALIDATION) {
                ImmutableValidationJobMessage validationJob = ImmutableValidationJobMessage.builder()
                        .entry(message.entry())
                        .retryStatistics(ImmutableRetryStatistics.of(5))
                        .build();
                messagingService.submitValidationJob(validationJob);
            } else if (taskCategory == TaskCategory.CONVERSION) {
                ImmutableConversionJobMessage conversionJob = ImmutableConversionJobMessage.builder()
                        .entry(message.entry())
                        .retryStatistics(ImmutableRetryStatistics.of(5))
                        .build();
                messagingService.submitConversionJob(conversionJob);
            }
        } else {
            messagingService.updateJobProcessingStatus(message, ProcessingState.COMPLETE);
        }
    }

    private Optional<TaskCategory> nextSubtaskToRun(ImmutableDelegationJobMessage jobDescription) {
        Optional<TaskCategory> subtask = getNextSubtaskToRun(jobDescription);

        if (subtask.isPresent()
            && subtask.get().equals(DEFAULT_TASK_CATEGORY)
            && jobDescription.entry().started() == null) {
            messagingService.updateJobProcessingStatus(jobDescription, ProcessingState.START);
        }
        messagingService.updateJobProcessingStatus(jobDescription, ProcessingState.UPDATE);

        return subtask;
    }

    private Optional<TaskCategory> getNextSubtaskToRun(ImmutableDelegationJobMessage jobDescription) {
        List<Task> allTasks = taskRepository.findTasks(jobDescription.entry().id());

        Set<TaskCategory> completedTaskCategories =
            Streams.filter(allTasks, (task -> task.completed() != null))
            .map(this::asTaskCategory)
            .filter(Objects::nonNull)
            .toSet();

        List<TaskCategory> potentialSubtasksToRun =
            Streams.map(allTasks, this::asTaskCategory)
            .filter(Objects::nonNull)
            .toList();

        potentialSubtasksToRun.add(TaskCategory.VALIDATION);  // default subtask if nothing else is detected
        potentialSubtasksToRun.removeAll(completedTaskCategories);

        if (potentialSubtasksToRun.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(potentialSubtasksToRun.get(0));
        }
    }

    @VisibleForTesting
    protected TaskCategory asTaskCategory(Task task) {
        String subtask = task.name().split("\\.")[0];
        return switch (subtask) {
            case "validation" -> TaskCategory.VALIDATION;
            case "conversion" -> TaskCategory.CONVERSION;
            default -> TaskCategory.RULE;  // XXX: Rules aren't actually convertable like this, so this might not be sensible
        };
    }
}
