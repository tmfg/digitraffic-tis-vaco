package fi.digitraffic.tis.vaco.process.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.messaging.model.Retryable;
import fi.digitraffic.tis.vaco.queuehandler.model.QueueEntry;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableJobMessage.class)
@JsonDeserialize(as = ImmutableJobMessage.class)
public interface JobMessage extends Retryable {
    QueueEntry message();
    @Nullable
    String previous();
}
