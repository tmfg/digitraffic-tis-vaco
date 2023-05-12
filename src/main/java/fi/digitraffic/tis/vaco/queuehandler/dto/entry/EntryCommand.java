package fi.digitraffic.tis.vaco.queuehandler.dto.entry;

import com.fasterxml.jackson.databind.JsonNode;
import fi.digitraffic.tis.vaco.queuehandler.dto.ConversionCommand;
import fi.digitraffic.tis.vaco.queuehandler.dto.ValidationCommand;

import javax.validation.constraints.NotNull;

public record EntryCommand(
    @NotNull
    String format,

    @NotNull
    String url,

    String etag,

    JsonNode metadata,

    ValidationCommand validation,

    ConversionCommand conversion
) {
}
