package fi.digitraffic.tis.vaco.delegator;

import fi.digitraffic.tis.vaco.VacoException;
import fi.digitraffic.tis.vaco.conversion.model.ImmutableConversionJobMessage;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
import fi.digitraffic.tis.vaco.messaging.model.RetryStatistics;
import fi.digitraffic.tis.vaco.queuehandler.model.ProcessingState;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationJobMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DelegationJobQueueSqsListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DelegationJobQueueSqsListener.class);

    private final MessagingService messagingService;

    public DelegationJobQueueSqsListener(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @SqsListener(QueueNames.VACO_JOBS)
    public void listenVacoJobs(ImmutableDelegationJobMessage jobDescription, Acknowledgement acknowledgement) {
        try {
            Optional<ImmutableDelegationJobMessage> job = countTry(jobDescription);

            if (job.isPresent()) {
                try {
                    jobDescription = job.get();
                    if (jobDescription.previous() == null) {
                        messagingService.updateJobProcessingStatus(jobDescription, ProcessingState.START);
                    } else if ("jobs".equals(jobDescription.previous())) {
                        messagingService.updateJobProcessingStatus(jobDescription, ProcessingState.COMPLETE);
                    } else {
                        messagingService.updateJobProcessingStatus(jobDescription, ProcessingState.UPDATE);
                    }

                    if (jobDescription.previous() == null) {
                        LOGGER.info("Got message {} without previous, sending to validation", jobDescription);
                        ImmutableValidationJobMessage validationJob = ImmutableValidationJobMessage.builder().message(jobDescription.entry()).build();
                        messagingService.submitValidationJob(validationJob);
                    } else if ("validation".equals(jobDescription.previous())) {
                        LOGGER.info("Got message {} from validation, sending to conversion", jobDescription);
                        ImmutableConversionJobMessage conversionJob = ImmutableConversionJobMessage.builder().message(jobDescription.entry()).build();
                        messagingService.submitConversionJob(conversionJob);
                    } else if ("conversion".equals(jobDescription.previous())) {
                        LOGGER.info("Got message {} from conversion, sending back to self with termination", jobDescription);
                        messagingService.submitProcessingJob(jobDescription.withPrevious("jobs"));
                    } else {
                        LOGGER.warn("unhandled job source, do nothing: {}", jobDescription);
                    }
                } catch (VacoException e) {
                    LOGGER.warn("Unhandled exception during message processing", e);
                }
            } else {
                messagingService.updateJobProcessingStatus(jobDescription, ProcessingState.COMPLETE);
                LOGGER.warn("Job for entry {} ran out of retries, skipping processing", jobDescription.entry().publicId());
            }
        } finally {
            acknowledgement.acknowledge();
        }

    }

    private Optional<ImmutableDelegationJobMessage> countTry(ImmutableDelegationJobMessage jobDescription) {
        RetryStatistics retryStatistics = jobDescription.retryStatistics();
        int retryCount = retryStatistics.retryCount() - 1;
        if (retryCount <= 0) {
            LOGGER.warn("Job for entry {} retried too many times! Cancelling processing and marking the job as done...", jobDescription.entry().publicId());
            return Optional.empty();
        } else {
            LOGGER.debug("Job for entry {} at try {} of {}", jobDescription.entry().publicId(), (retryStatistics.maxRetries() - retryCount), retryStatistics.maxRetries());
            return Optional.of(jobDescription.withRetryStatistics(ImmutableRetryStatistics.copyOf(retryStatistics).withRetryCount(retryCount)));
        }
    }

}
