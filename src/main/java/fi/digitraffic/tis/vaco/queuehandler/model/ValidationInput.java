package fi.digitraffic.tis.vaco.queuehandler.model;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableValidationInput.class)
@JsonDeserialize(as = ImmutableValidationInput.class)
public interface ValidationInput {
    @Nullable
    @JsonView(DataVisibility.Internal.class)
    Long id();

    @Value.Parameter
    String name();

    // TODO: This needs to be redefined as Jackson (de)serializable type hierarchy
    @Nullable
    JsonNode config();
}
