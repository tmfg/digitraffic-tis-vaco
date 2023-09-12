package fi.digitraffic.tis.vaco.validation.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.messaging.model.Retryable;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableValidationJobMessage.class)
@JsonDeserialize(as = ImmutableValidationJobMessage.class)
public interface ValidationJobMessage extends Retryable {
    Entry entry();
    @Nullable
    String previous();

    @Nullable
    FileReferences fileReferences();
}
