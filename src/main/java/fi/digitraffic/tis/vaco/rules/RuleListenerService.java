package fi.digitraffic.tis.vaco.rules;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.rules.model.ErrorMessage;
import fi.digitraffic.tis.vaco.rules.model.ValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.rules.validation.gtfs.CanonicalGtfsValidatorRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * SQS listener handles for rules which are implemented as part of VACO.
 */
@Service
public class RuleListenerService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final MessagingService messagingService;

    private final ErrorHandlerService errorHandlerService;

    private final ObjectMapper objectMapper;
    private final CanonicalGtfsValidatorRule canonicalGtfsValidatorRule;

    public RuleListenerService(MessagingService messagingService,
                               ErrorHandlerService errorHandlerService,
                               ObjectMapper objectMapper,
                               CanonicalGtfsValidatorRule canonicalGtfsValidatorRule) {
        this.messagingService = messagingService;
        this.errorHandlerService = errorHandlerService;
        this.objectMapper = objectMapper;
        this.canonicalGtfsValidatorRule = canonicalGtfsValidatorRule;
    }

    @Scheduled(fixedRateString = "${vaco.scheduling.canonical-gtfs-validation-rule.poll-rate}")
    public void handleCanonicalGtfsQueue() {
        listen(MessageQueue.RULES.munge(CanonicalGtfsValidatorRule.RULE_NAME), ValidationRuleJobMessage.class, canonicalGtfsValidatorRule::execute);
    }

    @Scheduled(fixedRateString = "${vaco.scheduling.errors.poll-rate}")
    public void handleErrorsQueue() {
        listen(MessageQueue.ERRORS.getQueueName(), ErrorMessage.class, this::handleErrors);
    }

    private CompletableFuture<Boolean> handleErrors(ErrorMessage errorMessage) {
        return CompletableFuture.supplyAsync(() -> errorHandlerService.reportErrors(errorMessage.errors()));
    }

    private <M, R> void listen(String queueName, Class<M> cls, Function<M, CompletableFuture<R>> function) {
        try {
            List<R> x = messagingService.readMessages(queueName)
                .map(m -> {
                    try {
                        logger.trace("Processing message {}", m);
                        M message = objectMapper.readValue(m.body(), cls);
                        return function.apply(message);
                    } catch (JsonProcessingException e) {
                        throw new RuleExecutionException("Failed to deserialize message in queue " + queueName, e);
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
}
