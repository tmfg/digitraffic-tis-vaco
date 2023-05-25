package fi.digitraffic.tis.vaco.queuehandler.dto.entry;

import com.fasterxml.jackson.annotation.JsonProperty;
import fi.digitraffic.tis.vaco.utils.Link;
import jakarta.annotation.Nullable;

import java.util.Map;

public record QueueHandlerResource<D>(
    @JsonProperty("data")
    D data,

    @Nullable
    @JsonProperty("links")
    Map<String, Link> links) {
}
