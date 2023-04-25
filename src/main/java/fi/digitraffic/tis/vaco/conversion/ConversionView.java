package fi.digitraffic.tis.vaco.conversion;

import fi.digitraffic.tis.vaco.conversion.steps.ConversionStepView;

import java.util.List;

public record ConversionView(
    // Maybe? Just an idea
    List<ConversionStepView> steps,
    List<Error> errors
    // Also potentially some link for download here
    ) {
}
