package fi.digitraffic.tis.vaco.queuehandler.dto.entry;

import fi.digitraffic.tis.vaco.utils.Link;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record EntryResultResource(@NotNull String entryId, Map<String, Link> links) {
}
