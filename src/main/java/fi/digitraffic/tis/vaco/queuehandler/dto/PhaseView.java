package fi.digitraffic.tis.vaco.queuehandler.dto;

import jakarta.validation.constraints.NotBlank;

public record PhaseView(
    @NotBlank
    String name,

    @NotBlank
    String summary,

    @NotBlank
    long timestamp
) {
}
