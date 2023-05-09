package fi.digitraffic.tis.vaco.queuehandler.dto.entry;

import fi.digitraffic.tis.vaco.queuehandler.dto.PhaseView;

import java.util.List;

public record EntryStatus(

    String summary,

    List<PhaseView> steps
) {
}
