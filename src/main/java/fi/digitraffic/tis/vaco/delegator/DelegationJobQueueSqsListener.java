package fi.digitraffic.tis.vaco.delegator;

import fi.digitraffic.tis.vaco.VacoException;
import fi.digitraffic.tis.vaco.conversion.model.ImmutableConversionJobMessage;
import fi.digitraffic.tis.vaco.delegator.model.Subtask;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
import fi.digitraffic.tis.vaco.messaging.model.RetryStatistics;
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
public class DelegationJobQueueSqsListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DelegationJobQueueSqsListener.class);
    private static final Subtask DEFAULT_SUBTASK = Subtask.VALIDATION;

    private final MessagingService messagingService;
    private final QueueHandlerRepository queueHandlerRepository;

    public DelegationJobQueueSqsListener(MessagingService messagingService,
                                         QueueHandlerRepository queueHandlerRepository) {
        this.messagingService = messagingService;
        this.queueHandlerRepository = queueHandlerRepository;
    }

    @SqsListener(QueueNames.VACO_JOBS)
    public void listenVacoJobs(ImmutableDelegationJobMessage jobDescription, Acknowledgement acknowledgement) {
        try {
            Optional<ImmutableDelegationJobMessage> job = shouldTry(jobDescription);

            if (job.isPresent()) {
                jobDescription = job.get();
                try {
                    Optional<Subtask> taskToRun = nextSubtaskToRun(job.get());

                    if (taskToRun.isPresent()) {
                        runTask(jobDescription, taskToRun);
                    } else {
                        messagingService.updateJobProcessingStatus(jobDescription, ProcessingState.COMPLETE);
                    }

                } catch (VacoException e) {
                    LOGGER.warn("Unhandled exception during message processing, requeuing message for retrying", e);
                    requeueMessage(jobDescription);
                }
            } else {
                messagingService.updateJobProcessingStatus(jobDescription, ProcessingState.COMPLETE);
                LOGGER.warn("Job for entry {} ran out of retries, skipping processing", jobDescription.entry().publicId());
            }
        } finally {
            acknowledgement.acknowledge();
        }
    }

    private void runTask(ImmutableDelegationJobMessage jobDescription, Optional<Subtask> taskToRun) {
        LOGGER.info("Job for entry {} next task to run {}", jobDescription.entry().publicId(), taskToRun.get());

        switch (taskToRun.get()) {
            case VALIDATION -> {
                ImmutableValidationJobMessage validationJob = ImmutableValidationJobMessage.builder().message(jobDescription.entry()).build();
                messagingService.submitValidationJob(validationJob);
            }
            case CONVERSION -> {
                ImmutableConversionJobMessage conversionJob = ImmutableConversionJobMessage.builder().message(jobDescription.entry()).build();
                messagingService.submitConversionJob(conversionJob);
            }
        }
    }

    private void requeueMessage(ImmutableDelegationJobMessage jobDescription) {
        ImmutableRetryStatistics stats = ImmutableRetryStatistics.copyOf(jobDescription.retryStatistics());
        ImmutableDelegationJobMessage requeueableMessage = jobDescription.withRetryStatistics(stats.withTryNumber(stats.tryNumber() + 1));
        messagingService.submitProcessingJob(requeueableMessage);
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

    private Optional<ImmutableDelegationJobMessage> shouldTry(ImmutableDelegationJobMessage jobDescription) {
        RetryStatistics retryStatistics = jobDescription.retryStatistics();

        if (retryStatistics.tryNumber() > retryStatistics.maxRetries()) {
            LOGGER.warn("Job for entry {} retried too many times! Cancelling processing and marking the job as done...", jobDescription.entry().publicId());
            return Optional.empty();
        } else {
            LOGGER.debug("Job for entry {} at try {} of {}", jobDescription.entry().publicId(), retryStatistics.tryNumber(), retryStatistics.maxRetries());
            return Optional.of(jobDescription);
        }
    }

}
