package fi.digitraffic.tis.vaco.delegator;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.VisibleForTesting;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.conversion.ConversionService;
import fi.digitraffic.tis.vaco.conversion.model.ImmutableConversionJobMessage;
import fi.digitraffic.tis.vaco.delegator.model.TaskCategory;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.SqsListenerBase;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
import fi.digitraffic.tis.vaco.process.TaskRepository;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.validation.ValidationService;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationJobMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
public class DelegationJobQueueSqsListener extends SqsListenerBase<ImmutableDelegationJobMessage> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DelegationJobQueueSqsListener.class);
    private static final TaskCategory DEFAULT_TASK_CATEGORY = TaskCategory.VALIDATION;

    private final MessagingService messagingService;
    private final TaskRepository taskRepository;
    private final ValidationService validationService;
    private final ConversionService conversionService;

    public DelegationJobQueueSqsListener(MessagingService messagingService,
                                         TaskRepository taskRepository,
                                         ValidationService validationService,
                                         ConversionService conversionService) {
        super((message, stats) -> messagingService.submitProcessingJob(message.withRetryStatistics(stats)));
        this.messagingService = messagingService;
        this.taskRepository = taskRepository;
        this.validationService = validationService;
        this.conversionService = conversionService;
    }

    @SqsListener(QueueNames.VACO_JOBS)
    public void listen(ImmutableDelegationJobMessage message, Acknowledgement acknowledgement) {
        handle(message, message.entry().publicId(), acknowledgement, (exhaustedRetries -> {
            messagingService.updateJobProcessingStatus(exhaustedRetries, ProcessingState.COMPLETE);
        }));
    }

    @Override
    protected void runTask(ImmutableDelegationJobMessage message) {
        Optional<TaskCategory> taskToRun = nextSubtaskToRun(message);

        if (taskToRun.isPresent()) {
            LOGGER.info("Job for entry {} next task to run {}", message.entry().publicId(), taskToRun.get());

            switch (taskToRun.get()) {
                case VALIDATION -> {
                    ImmutableValidationJobMessage validationJob = ImmutableValidationJobMessage.builder()
                        .entry(message.entry())
                        .retryStatistics(ImmutableRetryStatistics.of(5))
                        .build();
                    messagingService.submitValidationJob(validationJob);
                }
                case CONVERSION -> {
                    ImmutableConversionJobMessage conversionJob = ImmutableConversionJobMessage.builder()
                        .entry(message.entry())
                        .retryStatistics(ImmutableRetryStatistics.of(5))
                        .build();
                    messagingService.submitConversionJob(conversionJob);
                }
            }
        } else {
            messagingService.updateJobProcessingStatus(message, ProcessingState.COMPLETE);
        }
    }

    private Optional<TaskCategory> nextSubtaskToRun(ImmutableDelegationJobMessage jobDescription) {
        Optional<TaskCategory> subtask = getNextSubtaskToRun(jobDescription);

        if (subtask.isPresent()) {
            if (subtask.get().equals(DEFAULT_TASK_CATEGORY) && jobDescription.entry().started() == null) {
                messagingService.updateJobProcessingStatus(jobDescription, ProcessingState.START);
            }
        }
        messagingService.updateJobProcessingStatus(jobDescription, ProcessingState.UPDATE);

        return subtask;
    }

    private Optional<TaskCategory> getNextSubtaskToRun(ImmutableDelegationJobMessage jobDescription) {
        List<ImmutableTask> allTasks = taskRepository.findTasks(jobDescription.entry().id());

        Set<TaskCategory> completedTaskCategories =
            Streams.filter(allTasks, (task -> task.completed() != null))
            .map(DelegationJobQueueSqsListener::asTaskCategory)
            .filter(Objects::nonNull)
            .toSet();

        List<TaskCategory> potentialSubtasksToRun =
            Streams.map(allTasks, DelegationJobQueueSqsListener::asTaskCategory)
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
    protected static TaskCategory asTaskCategory(Task task) {
        String subtask = task.name().split("\\.")[0];
        TaskCategory s = switch (subtask) {
            case "validation" -> TaskCategory.VALIDATION;
            case "conversion" -> TaskCategory.CONVERSION;
            case "rule" -> TaskCategory.RULE;  // XXX: Rules aren't actually convertable like this, so this might not be sensible
            default -> null;
        };
        if (s == null) {
            LOGGER.warn("Unmappable task '{}'! {}", subtask, task);
        }
        return s;
    }
}
