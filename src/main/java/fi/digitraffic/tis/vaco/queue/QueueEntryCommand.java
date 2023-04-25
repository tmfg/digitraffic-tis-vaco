package fi.digitraffic.tis.vaco.queue;

import fi.digitraffic.tis.vaco.conversion.ConversionCommand;
import fi.digitraffic.tis.vaco.validation.ValidationCommand;

import javax.validation.constraints.NotNull;

public record QueueEntryCommand(@NotNull InputMetadata input,
                                ValidationCommand validationCommand,
                                ConversionCommand conversionCommand) {
}
