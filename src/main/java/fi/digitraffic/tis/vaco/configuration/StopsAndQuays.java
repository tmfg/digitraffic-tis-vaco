package fi.digitraffic.tis.vaco.configuration;

import jakarta.validation.constraints.NotBlank;

public record StopsAndQuays(@NotBlank String sourceUrl) {
}
