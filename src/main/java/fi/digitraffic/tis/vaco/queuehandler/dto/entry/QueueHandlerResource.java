package fi.digitraffic.tis.vaco.queuehandler.dto.entry;

import fi.digitraffic.tis.vaco.utils.Link;
import jakarta.annotation.Nullable;

import java.util.Map;

public record QueueHandlerResource<D>(
    D data,

    @Nullable
    Map<String, Link> links) {
}
