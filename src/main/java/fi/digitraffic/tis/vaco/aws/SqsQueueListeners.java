package fi.digitraffic.tis.vaco.aws;

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
public class SqsQueueListeners {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqsQueueListeners.class);

    private final MessagingService messagingService;

    public SqsQueueListeners(MessagingService messagingService) {
        this.messagingService = messagingService;
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

}
