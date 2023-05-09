package fi.digitraffic.tis.vaco.queuehandler.dto;

import javax.validation.constraints.NotNull;

public record PhaseView(
    @NotNull
    String name,

    @NotNull
    String stepSummary,

    @NotNull
    long timestamp
) {
}
