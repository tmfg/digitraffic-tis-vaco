package fi.digitraffic.tis.vaco.aws;

import fi.digitraffic.tis.vaco.conversion.ConversionService;
import fi.digitraffic.tis.vaco.messaging.model.JobDescription;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
import fi.digitraffic.tis.vaco.validation.ValidationService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SqsQueueListeners {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqsQueueListeners.class);

    private final SqsMessagingService messagingService;

    private final ValidationService validationService;

    private final ConversionService conversionService;

    public SqsQueueListeners(SqsMessagingService messagingService,
                             ValidationService validationService,
                             ConversionService conversionService) {
        this.messagingService = messagingService;
        this.validationService = validationService;
        this.conversionService = conversionService;
    }

    @SqsListener(QueueNames.VACO_JOBS)
    public void listenVacoJobs(JobDescription jobDescription, Acknowledgement acknowledgement) {

        JobDescription jobjob = new JobDescription(jobDescription.message() + " via JOBS", "jobs");
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
    public void listenVacoJobsValidation(JobDescription jobDescription, Acknowledgement acknowledgement) {
        LOGGER.debug("Got message " + jobDescription + " from " + MessageQueue.JOBS_VALIDATION + ", do nothing...");
        try {
            validationService.validate();
            // TODO: possibly use return value from #validate() to produce the next step
            messagingService.sendMessage(MessageQueue.JOBS, new JobDescription(jobDescription.message() + " via VALIDATION", "validation"));
        } catch (Exception e) {
            LOGGER.warn("Unhandled exception caught during validation job processing", e);
        } finally {
            acknowledgement.acknowledge();
        }
    }

    @SqsListener(QueueNames.VACO_JOBS_CONVERSION)
    public void listenVacoJobsConversion(JobDescription jobDescription, Acknowledgement acknowledgement) {
        LOGGER.debug("Got message " + jobDescription + " from " + MessageQueue.JOBS_CONVERSION + ", do nothing...");
        try {
            conversionService.convert();
            // TODO: possibly use return value from #convert() to produce the next step
            messagingService.sendMessage(MessageQueue.JOBS, new JobDescription(jobDescription.message() + " via CONVERSION", "conversion"));
        } catch (Exception e) {
            LOGGER.warn("Unhandled exception caught during conversion job processing", e);
        } finally {
            acknowledgement.acknowledge();
        }
    }
}
