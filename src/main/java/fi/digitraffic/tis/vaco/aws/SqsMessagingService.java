package fi.digitraffic.tis.vaco.aws;

import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.JobDescription;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import io.awspring.cloud.sqs.operations.MessagingOperationFailedException;
import io.awspring.cloud.sqs.operations.SendResult;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SqsMessagingService implements MessagingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqsMessagingService.class);

    private final SqsTemplate sqsTemplate;

    public SqsMessagingService(SqsTemplate sqsTemplate) {
        this.sqsTemplate = sqsTemplate;
    }

    @Override
    public void sendMessage(MessageQueue messageQueue, JobDescription jobDescription) {
        try {
            SendResult<JobDescription> result = sqsTemplate.send(messageQueue.getQueueName(), jobDescription);
        } catch (MessagingOperationFailedException mofe) {
            LOGGER.warn("Failed to send message %s to queue %s".formatted(jobDescription, messageQueue), mofe);
        }
    }
}
