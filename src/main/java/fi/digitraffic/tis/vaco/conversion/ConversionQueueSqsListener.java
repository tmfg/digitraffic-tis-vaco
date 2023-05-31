package fi.digitraffic.tis.vaco.conversion;

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
public class ConversionQueueSqsListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConversionQueueSqsListener.class);

    private final MessagingService messagingService;

    private final ConversionService conversionService;

    public ConversionQueueSqsListener(MessagingService messagingService, ConversionService conversionService) {
        this.messagingService = messagingService;
        this.conversionService = conversionService;
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
