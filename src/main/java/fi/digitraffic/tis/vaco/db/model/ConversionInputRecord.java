package fi.digitraffic.tis.vaco.db.model;

import tools.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
public interface ConversionInputRecord {
    @Value.Parameter
    Long id();

    @Value.Parameter
    String name();

    @Nullable
    JsonNode config();
}
