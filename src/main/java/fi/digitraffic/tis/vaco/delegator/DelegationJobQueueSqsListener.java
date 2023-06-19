package fi.digitraffic.tis.vaco.delegator;

import fi.digitraffic.tis.vaco.conversion.model.ImmutableConversionJobMessage;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
import fi.digitraffic.tis.vaco.queuehandler.model.ProcessingState;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationJobMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
            if (jobDescription.previous() == null) {
                messagingService.updateJobProcessingStatus(jobDescription, ProcessingState.START);
            } else if ("jobs".equals(jobDescription.previous())) {
                messagingService.updateJobProcessingStatus(jobDescription, ProcessingState.COMPLETE);
            } else {
                messagingService.updateJobProcessingStatus(jobDescription, ProcessingState.UPDATE);
            }

            if (jobDescription.previous() == null) {
                LOGGER.info("Got message %s without previous, sending to validation".formatted(jobDescription));
                ImmutableValidationJobMessage validationJob = ImmutableValidationJobMessage.builder().message(jobDescription.entry()).build();
                messagingService.submitValidationJob(validationJob);
            } else if ("validation".equals(jobDescription.previous())) {
                LOGGER.info("Got message %s from validation, sending to conversion".formatted(jobDescription));
                ImmutableConversionJobMessage conversionJob = ImmutableConversionJobMessage.builder().message(jobDescription.entry()).build();
                messagingService.submitConversionJob(conversionJob);
            } else if ("conversion".equals(jobDescription.previous())) {
                LOGGER.info("Got message %s from conversion, sending back to self with termination".formatted(jobDescription));
                messagingService.submitProcessingJob(jobDescription.withPrevious("jobs"));
            } else {
                LOGGER.warn("unhandled job source, do nothing: %s".formatted(jobDescription));
            }
        } finally {
            acknowledgement.acknowledge();
        }

    }

}
