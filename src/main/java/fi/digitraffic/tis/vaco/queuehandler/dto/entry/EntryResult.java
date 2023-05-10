package fi.digitraffic.tis.vaco.queuehandler.dto.entry;

import java.util.List;

public record EntryResult(
    EntryStatus status,

    List<Error> errors,

    EntryView entry
) {
}
