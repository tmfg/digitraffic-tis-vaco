package fi.digitraffic.tis.vaco.db.model;

import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
public interface FindingRecord {
    Long id();

    String publicId();

    Long taskId();

    @Nullable
    Long rulesetId();

    @Nullable
    String message();

    @Nullable
    byte[] raw();

    @Value.Default
    default String severity() {
        return "UNKNOWN";
    }

    String source();
}
