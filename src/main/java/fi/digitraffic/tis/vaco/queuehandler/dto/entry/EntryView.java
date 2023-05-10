package fi.digitraffic.tis.vaco.queuehandler.dto.entry;

import fi.digitraffic.tis.vaco.conversion.ConversionView;
import fi.digitraffic.tis.vaco.validation.ValidationView;
import jakarta.validation.constraints.NotBlank;

public record EntryView(
    @NotBlank
    String format,

    @NotBlank
    String url,

    String etag,

    ValidationView validation,

    ConversionView conversion
) {
}
