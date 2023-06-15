package fi.digitraffic.tis.vaco.queuehandler.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableConversionInput.class)
@JsonDeserialize(as = ImmutableConversionInput.class)
public interface ConversionInput {
    String targetFormat();
}
