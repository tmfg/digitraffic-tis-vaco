package fi.digitraffic.tis.vaco.queuehandler.dto.entry;

import com.fasterxml.jackson.databind.JsonNode;
import fi.digitraffic.tis.vaco.queuehandler.dto.ConversionCommand;
import fi.digitraffic.tis.vaco.queuehandler.dto.ValidationCommand;
import jakarta.validation.constraints.NotBlank;

public record EntryCommand(
    @NotBlank
    String format,

    @NotBlank
    String url,

    String etag,

    JsonNode metadata,

    ValidationCommand validation,

    ConversionCommand conversion
    //ImmutableConversionCommand conversion
) {
}
