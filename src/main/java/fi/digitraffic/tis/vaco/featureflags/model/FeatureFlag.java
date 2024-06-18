package fi.digitraffic.tis.vaco.featureflags.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.time.ZonedDateTime;

@Value.Immutable
@JsonSerialize(as = ImmutableFeatureFlag.class)
@JsonDeserialize(as = ImmutableFeatureFlag.class)
public interface FeatureFlag {
    // feature flag uniqueness is defined by its name, hence no id/publicId
    @Value.Parameter
    String name();

    @Value.Default
    default boolean enabled() {
        return false;
    }

    @Nullable
    ZonedDateTime modified();

    @Nullable
    String modifiedBy();
}
