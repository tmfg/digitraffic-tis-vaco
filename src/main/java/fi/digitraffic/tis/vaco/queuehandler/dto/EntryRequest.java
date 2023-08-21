package fi.digitraffic.tis.vaco.queuehandler.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableEntryRequest.class)
@JsonDeserialize(as = ImmutableEntryRequest.class)
public interface EntryRequest {
    String getFormat();

    String getUrl();

    @Nullable
    String getEtag();

    String getBusinessId();

    @Nullable
    JsonNode getMetadata();

    List<JsonNode> getValidations();

    @Nullable
    Conversion getConversion();

    @Value.Immutable
    @JsonSerialize(as = ImmutableConversion.class)
    @JsonDeserialize(as = ImmutableConversion.class)
    interface Conversion {
        @Value.Parameter
        String targetFormat();
    }
}
