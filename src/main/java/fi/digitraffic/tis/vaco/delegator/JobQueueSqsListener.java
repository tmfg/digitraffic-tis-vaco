package fi.digitraffic.tis.vaco.delegator;

import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableJobDescription;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
import fi.digitraffic.tis.vaco.queuehandler.model.ProcessingState;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JobQueueSqsListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobQueueSqsListener.class);

    private final MessagingService messagingService;

    public JobQueueSqsListener(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @SqsListener(QueueNames.VACO_JOBS)
    public void listenVacoJobs(ImmutableJobDescription jobDescription, Acknowledgement acknowledgement) {
        try {
            if (jobDescription.previous() == null) {
                messagingService.updateJobProcessingStatus(jobDescription, ProcessingState.START);
            } else {
                messagingService.updateJobProcessingStatus(jobDescription, ProcessingState.UPDATE);
            }

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
        } finally {
            messagingService.updateJobProcessingStatus(jobDescription, ProcessingState.COMPLETE);
            acknowledgement.acknowledge();
        }

    }

}
