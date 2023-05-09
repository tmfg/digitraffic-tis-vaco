package fi.digitraffic.tis.vaco.conversion;

import java.util.List;

public record ConversionView(
    List<Error> errors
    // Also potentially some link for download here
    ) {
}
