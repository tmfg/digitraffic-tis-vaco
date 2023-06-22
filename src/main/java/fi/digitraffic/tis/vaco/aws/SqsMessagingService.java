package fi.digitraffic.tis.vaco.aws;

import fi.digitraffic.tis.vaco.conversion.model.ConversionJobMessage;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.DelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.queuehandler.repository.QueueHandlerRepository;
import fi.digitraffic.tis.vaco.validation.model.JobMessage;
import io.awspring.cloud.sqs.operations.MessagingOperationFailedException;
import io.awspring.cloud.sqs.operations.SendResult;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

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

    private <P> Optional<SendResult<P>> sendMessage(MessageQueue messageQueue, P payload) {
        try {
            return Optional.ofNullable(sqsTemplate.send(messageQueue.getQueueName(), payload));
        } catch (MessagingOperationFailedException mofe) {
            LOGGER.warn("Failed to send message %s to queue %s".formatted(payload, messageQueue), mofe);
        }
        return Optional.empty();
    }

    @Override
    public void submitProcessingJob(DelegationJobMessage delegationJobMessage) {
        Optional<SendResult<DelegationJobMessage>> result = sendMessage(MessageQueue.JOBS, delegationJobMessage);
    }

    @Override
    public void submitValidationJob(JobMessage jobDescription) {
        Optional<SendResult<JobMessage>> result = sendMessage(MessageQueue.JOBS_VALIDATION, jobDescription);
    }

    @Override
    public void submitConversionJob(ConversionJobMessage jobDescription) {
        Optional<SendResult<ConversionJobMessage>> result = sendMessage(MessageQueue.JOBS_CONVERSION, jobDescription);
    }

    public void updateJobProcessingStatus(ImmutableDelegationJobMessage jobDescription, ProcessingState state) {
        switch (state) {
            case START -> queueHandlerRepository.startEntryProcessing(jobDescription.entry());
            case UPDATE -> queueHandlerRepository.updateEntryProcessing(jobDescription.entry());
            case COMPLETE -> queueHandlerRepository.completeEntryProcessing(jobDescription.entry());
        }
    }
}
