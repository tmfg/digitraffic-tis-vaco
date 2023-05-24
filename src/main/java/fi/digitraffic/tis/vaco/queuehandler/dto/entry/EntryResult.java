package fi.digitraffic.tis.vaco.queuehandler.dto.entry;

import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableYay;

import java.util.List;

public record EntryResult(
    EntryStatus status,

    List<Error> errors,

    EntryView entry,

    ImmutableYay yay
) {
}
