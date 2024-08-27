package fi.digitraffic.tis.vaco.badges;

import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.JobMessage;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
import fi.digitraffic.tis.vaco.rules.model.ImmutableResultMessage;
import fi.digitraffic.tis.vaco.rules.model.ImmutableValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import fi.digitraffic.tis.vaco.rules.model.ValidationRuleJobMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

@Component
public class TestQueueListener {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final MessagingService messagingService;

    private final ConcurrentMap<String, List<JobMessage>> processingMessages = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Function<JobMessage, Boolean>> acks = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Function<ValidationRuleJobMessage, ResultMessage>> resultConverters = new ConcurrentHashMap<>();

    public TestQueueListener(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    public void shouldAck(String publicId, Function<JobMessage, Boolean> test) {
        acks.put(publicId, test);
    }

    public ConcurrentMap<String, List<JobMessage>> getProcessingMessages() {
        return processingMessages;
    }

    public void setResultConverter(String publicId, Function<ValidationRuleJobMessage, ResultMessage> converter) {
        resultConverters.put(publicId, converter);
    }

    @SqsListener("rules-processing-gtfs-canonical")
    public void listen(ImmutableValidationRuleJobMessage message, Acknowledgement acknowledgement) {
        try {
            logger.debug("Received message {}", message);
            processingMessages.computeIfAbsent("rules-processing-gtfs-canonical", k -> new CopyOnWriteArrayList<>()).add(message);
            messagingService.sendMessage(QueueNames.VACO_RULES_RESULTS, resultConverters.getOrDefault(message.entry().publicId(), this::defaultConverter).apply(message));
        } finally {
            if (shouldAcknowledge(message)) {
                logger.debug("Will acknowledge message {}", message.entry().publicId());
                acknowledgement.acknowledge();
            } else {
                logger.debug("Will NOT acknowledge message {}", message.entry().publicId());
            }
        }

    }

    private ResultMessage defaultConverter(ValidationRuleJobMessage jobMessage) {
        return ImmutableResultMessage.of(
            jobMessage.entry().publicId(),
            jobMessage.task().id(),
            jobMessage.source(),
            jobMessage.inputs(),
            jobMessage.outputs(),
            Map.of()
        );
    }

    private boolean shouldAcknowledge(JobMessage message) {
        return acks.getOrDefault(message.entry().publicId(), e -> true).apply(message);
    }
}
