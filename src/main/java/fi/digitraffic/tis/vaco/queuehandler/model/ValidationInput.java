package fi.digitraffic.tis.vaco.queuehandler.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableValidationInput;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableValidationInput.class)
@JsonDeserialize(as = ImmutableValidationInput.class)
public interface ValidationInput {
}
