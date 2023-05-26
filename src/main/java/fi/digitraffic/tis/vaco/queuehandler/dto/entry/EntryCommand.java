package fi.digitraffic.tis.vaco.queuehandler.dto.entry;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record EntryCommand(
    @NotBlank
    String format,

    @NotBlank
    String url,

    String etag,

    JsonNode metadata,

    Validation validation,

    Conversion conversion
) {
    public record Validation() {
    }

    public record Conversion() {
    }
}
