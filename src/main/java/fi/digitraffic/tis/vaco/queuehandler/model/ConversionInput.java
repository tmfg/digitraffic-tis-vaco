package fi.digitraffic.tis.vaco.queuehandler.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableConversionInput;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableConversionInput.class)
@JsonDeserialize(as = ImmutableConversionInput.class)
public interface ConversionInput {
}
