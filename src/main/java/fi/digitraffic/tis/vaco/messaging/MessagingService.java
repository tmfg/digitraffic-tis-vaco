package fi.digitraffic.tis.vaco.messaging;

import com.github.benmanes.caffeine.cache.Cache;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.conversion.model.ConversionJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.DelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.queuehandler.repository.QueueHandlerRepository;
import fi.digitraffic.tis.vaco.rules.model.ImmutableErrorMessage;
import fi.digitraffic.tis.vaco.rules.model.ValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.validation.model.ValidationJobMessage;
import io.awspring.cloud.sqs.operations.MessagingOperationFailedException;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@Service
public class MessagingService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final SqsClient sqsClient;
    private final SqsTemplate sqsTemplate;
    private final Cache<String, String> sqsQueueUrlCache;
    private final QueueHandlerRepository queueHandlerRepository;

    public MessagingService(SqsClient sqsClient,
                            SqsTemplate sqsTemplate,
                            Cache<String, String> sqsQueueUrlCache,
                            QueueHandlerRepository queueHandlerRepository) {
        this.sqsClient = sqsClient;
        this.sqsTemplate = sqsTemplate;
        this.sqsQueueUrlCache = sqsQueueUrlCache;
        this.queueHandlerRepository = queueHandlerRepository;
    }

    private <P> CompletableFuture<P> sendMessage(String queueName, P payload) {
        try {
            logger.debug("send {} <- {}", queueName, payload);
            return sqsTemplate.sendAsync(queueName, payload).thenApply(sr -> sr.message().getPayload());
        } catch (MessagingOperationFailedException mofe) {
            logger.warn("Failed to send message %s to queue %s".formatted(payload, queueName), mofe);
            return CompletableFuture.failedFuture(mofe);
        }
    }

    public CompletableFuture<DelegationJobMessage> submitProcessingJob(DelegationJobMessage delegationJobMessage) {
        return sendMessage(MessageQueue.JOBS.getQueueName(), delegationJobMessage);
    }

    public CompletableFuture<ValidationJobMessage> submitValidationJob(ValidationJobMessage jobDescription) {
        return sendMessage(MessageQueue.JOBS_VALIDATION.getQueueName(), jobDescription);
    }
    // TODO: reduce scopes of return values
   public CompletableFuture<ConversionJobMessage> submitConversionJob(ConversionJobMessage jobDescription) {
        return sendMessage(MessageQueue.JOBS_CONVERSION.getQueueName(), jobDescription);
    }

    public void updateJobProcessingStatus(ImmutableDelegationJobMessage jobDescription, ProcessingState state) {
        switch (state) {
            case START -> queueHandlerRepository.startEntryProcessing(jobDescription.entry());
            case UPDATE -> queueHandlerRepository.updateEntryProcessing(jobDescription.entry());
            case COMPLETE -> queueHandlerRepository.completeEntryProcessing(jobDescription.entry());
        }
    }

    public CompletableFuture<ValidationRuleJobMessage> submitRuleExecutionJob(String ruleName, ValidationRuleJobMessage ruleMessage) {
        return sendMessage(MessageQueue.RULES.munge(ruleName), ruleMessage);
    }

    public CompletableFuture<ImmutableErrorMessage> submitErrors(ImmutableErrorMessage message) {
        return sendMessage(MessageQueue.ERRORS.getQueueName(), message);
    }

    public void deleteMessage(String queueName, Message m) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
            .queueUrl(resolveQueueUrl(queueName))
            .receiptHandle(m.receiptHandle())
            .build());
        logger.debug("delete {} !- {}", queueName, m);
    }

    public Stream<Message> readMessages(String queueName) {
        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
            .queueUrl(resolveQueueUrl(queueName))
            .maxNumberOfMessages(Runtime.getRuntime().availableProcessors())
            .waitTimeSeconds(1)
            .build();

        return sqsClient.receiveMessage(receiveMessageRequest)
            .messages()
            .stream()
            .parallel()
            .peek(m -> logger.debug("receive {} -> {}", queueName, m));
    }

    private String resolveQueueUrl(String queueName) {
        return sqsQueueUrlCache.get(queueName, cachedQueueName -> {
            String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                .queueName(cachedQueueName)
                .build()).queueUrl();
            logger.debug("Resolved URL for queue {} as {}", cachedQueueName, queueUrl);
            return queueUrl;
        });

    }
}
