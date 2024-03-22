package fi.digitraffic.tis.vaco.api.model.queue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableCreateEntryRequest.class)
@JsonDeserialize(as = ImmutableCreateEntryRequest.class)
public interface CreateEntryRequest {
    String format();

    String name();

    String url();

    @Nullable
    String etag();

    String businessId();

    @Nullable
    JsonNode metadata();

    @Nullable
    List<JsonNode> validations();

    @Nullable
    List<JsonNode> conversions();

    @Value.Default
    default List<String> notifications() {
        return List.of();
    }
}
