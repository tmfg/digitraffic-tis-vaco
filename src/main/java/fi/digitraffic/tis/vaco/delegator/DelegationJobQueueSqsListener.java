package fi.digitraffic.tis.vaco.delegator;

import fi.digitraffic.tis.vaco.conversion.model.ImmutableConversionJobMessage;
import fi.digitraffic.tis.vaco.delegator.model.Subtask;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.SqsListenerBase;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutablePhase;
import fi.digitraffic.tis.vaco.queuehandler.model.Phase;
import fi.digitraffic.tis.vaco.queuehandler.model.ProcessingState;
import fi.digitraffic.tis.vaco.queuehandler.repository.QueueHandlerRepository;
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
import java.util.stream.Collectors;

@Component
public class DelegationJobQueueSqsListener extends SqsListenerBase<ImmutableDelegationJobMessage> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DelegationJobQueueSqsListener.class);
    private static final Subtask DEFAULT_SUBTASK = Subtask.VALIDATION;

    private final MessagingService messagingService;
    private final QueueHandlerRepository queueHandlerRepository;

    public DelegationJobQueueSqsListener(MessagingService messagingService,
                                         QueueHandlerRepository queueHandlerRepository) {
        super((message, stats) -> messagingService.submitProcessingJob(message.withRetryStatistics(stats)));
        this.messagingService = messagingService;
        this.queueHandlerRepository = queueHandlerRepository;
    }

    @SqsListener(QueueNames.VACO_JOBS)
    public void listen(ImmutableDelegationJobMessage message, Acknowledgement acknowledgement) {
        handle(message, message.entry().publicId(), acknowledgement, (exhaustedRetries -> {
            messagingService.updateJobProcessingStatus(exhaustedRetries, ProcessingState.COMPLETE);
        }));
    }

    @Override
    protected void runTask(ImmutableDelegationJobMessage message) {
        Optional<Subtask> taskToRun = nextSubtaskToRun(message);

        if (taskToRun.isPresent()) {
            LOGGER.info("Job for entry {} next task to run {}", message.entry().publicId(), taskToRun.get());

            switch (taskToRun.get()) {
                case VALIDATION -> {
                    ImmutableValidationJobMessage validationJob = ImmutableValidationJobMessage.builder()
                        .message(message.entry())
                        .retryStatistics(ImmutableRetryStatistics.of(5))
                        .build();
                    messagingService.submitValidationJob(validationJob);
                }
                case CONVERSION -> {
                    ImmutableConversionJobMessage conversionJob = ImmutableConversionJobMessage.builder()
                        .message(message.entry())
                        .build();
                    messagingService.submitConversionJob(conversionJob);
                }
            }
        } else {
            messagingService.updateJobProcessingStatus(message, ProcessingState.COMPLETE);
        }
    }

    private Optional<Subtask> nextSubtaskToRun(ImmutableDelegationJobMessage jobDescription) {
        Optional<Subtask> subtask = getNextSubtaskToRun(jobDescription);

        if (subtask.isPresent()) {
            if (subtask.get().equals(DEFAULT_SUBTASK) && jobDescription.entry().started() == null) {
                messagingService.updateJobProcessingStatus(jobDescription, ProcessingState.START);
            }
        }
        messagingService.updateJobProcessingStatus(jobDescription, ProcessingState.UPDATE);

        return subtask;
    }

    private Optional<Subtask> getNextSubtaskToRun(ImmutableDelegationJobMessage jobDescription) {
        List<ImmutablePhase> allPhases = queueHandlerRepository.findPhases(jobDescription.entry());

        Set<Subtask> completedSubtasks = allPhases.stream()
            .filter(phase -> phase.completed() != null)
            .map(DelegationJobQueueSqsListener::asSubtask)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        List<Subtask> potentialSubtasksToRun = allPhases.stream()
            .map(DelegationJobQueueSqsListener::asSubtask)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        potentialSubtasksToRun.add(Subtask.VALIDATION);  // default subtask if nothing else is detected
        potentialSubtasksToRun.removeAll(completedSubtasks);

        if (potentialSubtasksToRun.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(potentialSubtasksToRun.get(0));
        }
    }

    private static Subtask asSubtask(Phase phase) {
        String subtask = phase.name().split("\\.")[0];
        Subtask s = switch (subtask) {
            case "validation" -> Subtask.VALIDATION;
            case "conversion" -> Subtask.CONVERSION;
            default -> null;
        };
        if (s == null) {
            LOGGER.warn("Unmappable phase '{}'! {}", subtask, phase);
        }
        return s;
    }
}
