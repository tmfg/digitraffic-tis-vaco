package fi.digitraffic.tis.vaco.queuehandler.dto.entry;

import fi.digitraffic.tis.vaco.conversion.ConversionView;
import fi.digitraffic.tis.vaco.queuehandler.dto.Metadata;
import fi.digitraffic.tis.vaco.validation.ValidationView;

import javax.validation.constraints.NotNull;

public record EntryView(EntryStatus status,
                        @NotNull Metadata input,
                        ValidationView validation,
                        ConversionView conversion) {
}
