package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableJobDescription;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
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

    public ValidationQueueSqsListener(MessagingService messagingService, ValidationService validationService) {
        this.messagingService = messagingService;
        this.validationService = validationService;
    }

    @SqsListener(QueueNames.VACO_JOBS_VALIDATION)
    public void listenVacoJobsValidation(ImmutableJobDescription jobDescription, Acknowledgement acknowledgement) {
        LOGGER.info("Got message " + jobDescription + " from " + MessageQueue.JOBS_VALIDATION + ", validate...");
        try {
            ImmutableJobDescription updated = validationService.validate(jobDescription);
            messagingService.sendMessage(MessageQueue.JOBS, updated.withPrevious("validation"));
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
