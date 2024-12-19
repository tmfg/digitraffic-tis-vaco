package fi.digitraffic.tis.aws.sqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.rules.RuleExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class SqsListener {
    private final Logger logger = LoggerFactory.getLogger(getClass());;

    protected final MessagingService messagingService;
    private final ObjectMapper objectMapper;

    public SqsListener(MessagingService messagingService, ObjectMapper objectMapper) {
        this.messagingService = Objects.requireNonNull(messagingService);
        this.objectMapper = Objects.requireNonNull(objectMapper);

    }

    protected <M, R> void listen(String queueName, Function<String, M> read, Function<M, CompletableFuture<R>> process) {
        try {
            messagingService.readMessages(queueName)
                .map(m -> {
                    try {
                        logger.trace("Processing message {}", m);
                        M message = read.apply(m.body());
                        return process.apply(message);
                    } finally {
                        logger.trace("Deleting message {}", m);
                        messagingService.deleteMessage(queueName, m);
                    }
                })
                .map(CompletableFuture::join)
                .toList();
        } catch (SqsException e) {
            logger.warn("Failed to process messages from queue {}", queueName, e);
        }
    }

    protected <R> void listenTree(String queueName, Function<JsonNode, CompletableFuture<R>> process) {

        Function<String, JsonNode> x = message -> {
            try {
                return objectMapper.readTree(message);
            } catch (JsonProcessingException e) {
                throw new RuleExecutionException("Failed to deserialize message in queue " + queueName, e);
            }
        };
        listen(queueName, x, process);
    }

    protected <M, R> void listenValue(String queueName, Class<M> cls, Function<M, CompletableFuture<R>> process) {

        Function<String, M> x = message -> {
            try {
                return objectMapper.readValue(message, cls);
            } catch (JsonProcessingException e) {
                throw new RuleExecutionException("Failed to deserialize message in queue " + queueName, e);
            }
        };
        listen(queueName, x, process);
    }

}
