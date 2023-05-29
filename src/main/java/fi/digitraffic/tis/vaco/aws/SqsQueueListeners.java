package fi.digitraffic.tis.vaco.aws;

import fi.digitraffic.tis.vaco.conversion.ConversionService;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableJobDescription;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
import fi.digitraffic.tis.vaco.validation.ValidationProcessException;
import fi.digitraffic.tis.vaco.validation.ValidationService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SqsQueueListeners {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqsQueueListeners.class);

    private final MessagingService messagingService;

    private final ValidationService validationService;

    private final ConversionService conversionService;

    public SqsQueueListeners(MessagingService messagingService,
                             ValidationService validationService,
                             ConversionService conversionService) {
        this.messagingService = messagingService;
        this.validationService = validationService;
        this.conversionService = conversionService;
    }

    @SqsListener(QueueNames.VACO_JOBS)
    public void listenVacoJobs(ImmutableJobDescription jobDescription, Acknowledgement acknowledgement) {
        ImmutableJobDescription jobjob = jobDescription.withPrevious("jobs");

        if (jobDescription.previous() == null) {
            LOGGER.info("Got message %s without previous, sending to validation".formatted(jobDescription));
            messagingService.sendMessage(MessageQueue.JOBS_VALIDATION, jobjob);
        } else if ("validation".equals(jobDescription.previous())) {
            LOGGER.info("Got message %s from validation, sending to conversion".formatted(jobDescription));
            messagingService.sendMessage(MessageQueue.JOBS_CONVERSION, jobjob);
        } else if ("conversion".equals(jobDescription.previous())) {
            LOGGER.info("Got message %s from conversion, sending back to self with termination".formatted(jobjob));
            messagingService.sendMessage(MessageQueue.JOBS, jobjob);
        } else {
            LOGGER.warn("unknown job source, do nothing: %s".formatted(jobDescription));
        }

        acknowledgement.acknowledge();
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

    @SqsListener(QueueNames.VACO_JOBS_CONVERSION)
    public void listenVacoJobsConversion(ImmutableJobDescription jobDescription, Acknowledgement acknowledgement) {
        LOGGER.info("Got message " + jobDescription + " from " + MessageQueue.JOBS_CONVERSION + ", convert...");
        try {
            ImmutableJobDescription updated = conversionService.convert(jobDescription);
            messagingService.sendMessage(MessageQueue.JOBS, updated.withPrevious("conversion"));
        } catch (Exception e) {
            LOGGER.warn("Unhandled exception caught during conversion job processing", e);
        } finally {
            acknowledgement.acknowledge();
        }
    }
}
