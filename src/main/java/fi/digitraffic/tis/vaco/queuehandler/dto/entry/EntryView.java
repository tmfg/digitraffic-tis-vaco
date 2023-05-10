package fi.digitraffic.tis.vaco.queuehandler.dto.entry;

import fi.digitraffic.tis.vaco.conversion.ConversionView;
import fi.digitraffic.tis.vaco.validation.ValidationView;

import javax.validation.constraints.NotNull;

public record EntryView(
    @NotNull
    String format,

    @NotNull
    String url,

    String etag,

    ValidationView validation,

    ConversionView conversion
) {
}
