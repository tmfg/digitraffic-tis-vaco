package fi.digitraffic.tis.vaco.aws;

import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableJobDescription;
import fi.digitraffic.tis.vaco.messaging.model.JobDescription;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.queuehandler.model.ProcessingState;
import fi.digitraffic.tis.vaco.queuehandler.repository.QueueHandlerRepository;
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
    private final QueueHandlerRepository queueHandlerRepository;

    public SqsMessagingService(SqsTemplate sqsTemplate,
                               QueueHandlerRepository queueHandlerRepository) {
        this.sqsTemplate = sqsTemplate;
        this.queueHandlerRepository = queueHandlerRepository;
    }

    @Override
    public void sendMessage(MessageQueue messageQueue, JobDescription jobDescription) {
        try {
            SendResult<JobDescription> result = sqsTemplate.send(messageQueue.getQueueName(), jobDescription);
        } catch (MessagingOperationFailedException mofe) {
            LOGGER.warn("Failed to send message %s to queue %s".formatted(jobDescription, messageQueue), mofe);
        }
    }

    public void updateJobProcessingStatus(ImmutableJobDescription jobDescription, ProcessingState state) {
        switch (state) {
            case START -> queueHandlerRepository.startEntryProcessing(jobDescription.message());
            case UPDATE -> queueHandlerRepository.updateEntryProcessing(jobDescription.message());
            case COMPLETE -> queueHandlerRepository.completeEntryProcessing(jobDescription.message());
        }
    }
}
