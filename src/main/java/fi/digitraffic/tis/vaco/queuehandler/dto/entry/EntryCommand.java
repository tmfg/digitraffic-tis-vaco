package fi.digitraffic.tis.vaco.queuehandler.dto.entry;

import fi.digitraffic.tis.vaco.queuehandler.dto.ConversionCommand;
import fi.digitraffic.tis.vaco.queuehandler.dto.Metadata;
import fi.digitraffic.tis.vaco.queuehandler.dto.ValidationCommand;

import javax.validation.constraints.NotNull;

public record EntryCommand(
    @NotNull
    Metadata input,

    ValidationCommand validation,

    ConversionCommand conversion
) {
}
