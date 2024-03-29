package fi.digitraffic.tis.vaco.db.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
public interface ValidationInputRecord {
    @Value.Parameter
    Long id();

    @Value.Parameter
    String name();

    @Nullable
    JsonNode config();
}
