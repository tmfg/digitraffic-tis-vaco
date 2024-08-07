package fi.digitraffic.tis.vaco.badges;

import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationJobMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.springframework.stereotype.Component;

@Component
public class TestQueueListener {

    @SqsListener("rules-processing-gtfs-canonical")
    public void listen(ImmutableValidationJobMessage message, Acknowledgement acknowledgement) {
        System.out.println("message = " + message + ", acknowledgement = " + acknowledgement);

    }
}
