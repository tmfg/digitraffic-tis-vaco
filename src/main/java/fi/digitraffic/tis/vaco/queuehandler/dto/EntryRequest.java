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

    @Nullable
    String getName();

    String getUrl();

    @Nullable
    String getEtag();

    String getBusinessId();

    @Nullable
    JsonNode getMetadata();

    @Nullable
    List<JsonNode> getValidations();

    @Nullable
    List<JsonNode> getConversions();

}
