package fi.digitraffic.tis.vaco.featureflags.model;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.time.LocalDateTime;

@Value.Immutable
@JsonSerialize(as = ImmutableFeatureFlag.class)
@JsonDeserialize(as = ImmutableFeatureFlag.class)
public interface FeatureFlag {
    @Nullable
    @JsonView(DataVisibility.InternalOnly.class)
    Long id();

    @Value.Parameter
    String name();

    @Value.Default
    default boolean enabled() {
        return false;
    }

    @Nullable
    LocalDateTime modified();

    @Nullable
    String modifiedBy();
}
