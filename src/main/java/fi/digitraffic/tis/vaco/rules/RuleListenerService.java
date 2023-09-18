package fi.digitraffic.tis.vaco.rules;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.rules.model.ValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.rules.validation.gtfs.CanonicalGtfsValidatorRule;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * SQS listener handles for rules which are implemented as part of VACO.
 */
@Service
public class RuleListenerService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final MessagingService messagingService;

    private final ObjectMapper objectMapper;
    private final CanonicalGtfsValidatorRule canonicalGtfsValidatorRule;

    public RuleListenerService(MessagingService messagingService,
                               ObjectMapper objectMapper,
                               CanonicalGtfsValidatorRule canonicalGtfsValidatorRule) {
        this.messagingService = messagingService;
        this.objectMapper = objectMapper;
        this.canonicalGtfsValidatorRule = canonicalGtfsValidatorRule;
    }

    @Scheduled(fixedRateString = "${vaco.scheduling.canonical-gtfs-validation-rule.poll-rate}")
    public void handleCanonicalGtfsQueue() {
        listen(MessageQueue.RULES.munge(CanonicalGtfsValidatorRule.RULE_NAME));
    }

    private void listen(String queueName) {
        try {
            List<ValidationReport> x = messagingService.readMessages(queueName)
                .map(m -> {
                    try {
                        ValidationRuleJobMessage message =  objectMapper.readValue(m.body(), ValidationRuleJobMessage.class);
                        return canonicalGtfsValidatorRule.execute(message);
                    } catch (JsonProcessingException e) {
                        throw new RuleExecutionException("Failed to deserialize message in queue " + queueName, e);
                    } finally {
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
