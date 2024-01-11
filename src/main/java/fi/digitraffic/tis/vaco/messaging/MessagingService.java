package fi.digitraffic.tis.vaco.messaging;

import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.caching.CachingFailureException;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.caching.model.CachedType;
import fi.digitraffic.tis.vaco.entries.EntryRepository;
import fi.digitraffic.tis.vaco.messaging.model.DelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
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

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@Service
public class MessagingService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final SqsClient sqsClient;
    private final SqsTemplate sqsTemplate;
    private final CachingService cachingService;
    private final EntryRepository entryRepository;

    public MessagingService(SqsClient sqsClient,
                            SqsTemplate sqsTemplate,
                            CachingService cachingService,
                            EntryRepository entryRepository) {
        this.sqsClient = Objects.requireNonNull(sqsClient);
        this.sqsTemplate = Objects.requireNonNull(sqsTemplate);
        this.cachingService = Objects.requireNonNull(cachingService);
        this.entryRepository = Objects.requireNonNull(entryRepository);
    }

    public <P> CompletableFuture<P> sendMessage(String queueName, P payload) {
        try {
            logger.debug("send {} <- {}", queueName, payload);
            return sqsTemplate.sendAsync(queueName, payload).thenApply(sr -> sr.message().getPayload());
        } catch (MessagingOperationFailedException mofe) {
            logger.warn("Failed to send message %s to queue %s".formatted(payload, queueName), mofe);
            return CompletableFuture.failedFuture(mofe);
        }
    }

    public CompletableFuture<DelegationJobMessage> submitProcessingJob(DelegationJobMessage delegationJobMessage) {
        if (delegationJobMessage.entry().findings() != null
            && !delegationJobMessage.entry().findings().isEmpty()) {
            throw new RuntimeException("fail here" + delegationJobMessage);
        }
        return sendMessage(MessageQueue.JOBS.getQueueName(), delegationJobMessage);
    }

    public CompletableFuture<ValidationJobMessage> submitValidationJob(ValidationJobMessage jobDescription) {
        return sendMessage(MessageQueue.JOBS_VALIDATION.getQueueName(), jobDescription);
    }

    public void updateJobProcessingStatus(ImmutableDelegationJobMessage jobDescription, ProcessingState state) {
        switch (state) {
            case START -> entryRepository.startEntryProcessing(jobDescription.entry());
            case UPDATE -> entryRepository.updateEntryProcessing(jobDescription.entry());
            case COMPLETE -> entryRepository.completeEntryProcessing(jobDescription.entry());
        }
    }

    public CompletableFuture<ValidationRuleJobMessage> submitRuleExecutionJob(String ruleName, ValidationRuleJobMessage ruleMessage) {
        return sendMessage(MessageQueue.RULE_PROCESSING.munge(ruleName), ruleMessage);
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
        return cachingService.cache(CachedType.QUEUE_NAME, queueName, cachedQueueName -> {
            String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                .queueName(cachedQueueName)
                .build()).queueUrl();
            logger.debug("Resolved URL for queue {} as {}", cachedQueueName, queueUrl);
            return queueUrl;
        }).orElseThrow(() -> new CachingFailureException("Failed to cache " + queueName + " resolving!"));
    }
}
