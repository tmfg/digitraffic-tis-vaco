package fi.digitraffic.tis.vaco.queuehandler.dto;

import jakarta.annotation.Nullable;

import java.util.Map;

public record QueueHandlerResource<D>(
    D data,

    @Nullable
    Map<String, Link> links) {
}
