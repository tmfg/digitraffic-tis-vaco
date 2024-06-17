package fi.digitraffic.tis.vaco.db.model;


import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.time.ZonedDateTime;

@Value.Immutable
public interface FeatureFlagRecord {
    @Value.Parameter
    Long id();

    @Value.Default
    default boolean enabled() {
        return false;
    }

    @Value.Parameter
    String name();

    @Value.Parameter
    ZonedDateTime modified();

    @Nullable
    String modifiedBy();
}
