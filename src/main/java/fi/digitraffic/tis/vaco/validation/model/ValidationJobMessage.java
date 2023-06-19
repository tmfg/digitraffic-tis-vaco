package fi.digitraffic.tis.vaco.validation.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.queuehandler.model.QueueEntry;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableValidationJobMessage.class)
@JsonDeserialize(as = ImmutableValidationJobMessage.class)
public interface ValidationJobMessage {
    QueueEntry message();
    @Nullable
    String previous();
}
