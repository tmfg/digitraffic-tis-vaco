package fi.digitraffic.tis.vaco.queuehandler.model;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.time.LocalDateTime;

@Value.Immutable
@JsonSerialize(as = ImmutablePhase.class)
@JsonDeserialize(as = ImmutablePhase.class)
public interface Phase {
    @Nullable
    @JsonView(DataVisibility.Internal.class)
    Long id();

    @Nullable  // needs to be here for the Mapstruct automated mapping to work
    @JsonView(DataVisibility.Internal.class)
    @Value.Parameter
    Long entryId();

    @Value.Parameter
    String name();

    @Value.Parameter
    int priority();

    @Nullable
    LocalDateTime started();

    @Nullable
    LocalDateTime updated();

    @Nullable
    LocalDateTime completed();
}
