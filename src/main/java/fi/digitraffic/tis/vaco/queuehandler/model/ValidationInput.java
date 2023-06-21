package fi.digitraffic.tis.vaco.queuehandler.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.validation.model.PhaseResult;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableValidationInput.class)
@JsonDeserialize(as = ImmutableValidationInput.class)
public interface ValidationInput {
    @Nullable
    List<String> rules();

    @Nullable
    List<PhaseResult<?>> results();
}
