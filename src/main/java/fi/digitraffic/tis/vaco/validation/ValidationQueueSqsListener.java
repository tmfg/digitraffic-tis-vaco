package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
import fi.digitraffic.tis.vaco.queuehandler.repository.QueueHandlerRepository;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationJobMessage;
import fi.digitraffic.tis.vaco.validation.model.ValidationJobResult;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ValidationQueueSqsListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidationQueueSqsListener.class);

    private final MessagingService messagingService;

    private final ValidationService validationService;

    public ValidationQueueSqsListener(MessagingService messagingService,
                                      ValidationService validationService,
                                      QueueHandlerRepository queueHandlerRepository) {
        this.messagingService = messagingService;
        this.validationService = validationService;
    }

    @SqsListener(QueueNames.VACO_JOBS_VALIDATION)
    public void listenVacoJobsValidation(ImmutableValidationJobMessage jobDescription, Acknowledgement acknowledgement) {
        LOGGER.info("Got message " + jobDescription + " from " + MessageQueue.JOBS_VALIDATION + ", validate...");
        /* TODO: This method needs to be bulletproof for exceptions; each line should be wrapped in try...catch and the
                 final acknowledgment should always happen as well.
         */
        try {
            // TODO: We have full results available here, but don't do anything with them - maybe we don't need 'em?
            ValidationJobResult result = validationService.validate(jobDescription);

            ImmutableDelegationJobMessage job = ImmutableDelegationJobMessage.builder()
                .entry(jobDescription.message())
                .retryStatistics(ImmutableRetryStatistics.of(5))
                .build();
            messagingService.submitProcessingJob(job);
        } catch (ValidationProcessException vpe) {
            // TODO: update DB phase?
            LOGGER.warn("Unhandled exception caught during validation job processing", vpe);
        } catch (Exception e) {
            LOGGER.warn("Unhandled exception caught during validation job processing", e);
        } finally {
            acknowledgement.acknowledge();
        }
    }
}
