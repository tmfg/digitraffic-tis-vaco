package fi.digitraffic.tis.vaco.queuehandler.dto.entry;

import fi.digitraffic.tis.vaco.queuehandler.dto.ConversionCommand;
import fi.digitraffic.tis.vaco.queuehandler.dto.ValidationCommand;
import jakarta.validation.constraints.NotBlank;

public record EntryCommand(
    @NotBlank
    String format,

    @NotBlank
    String url,

    String etag,

    ValidationCommand validation,

    ConversionCommand conversion
) {
}
