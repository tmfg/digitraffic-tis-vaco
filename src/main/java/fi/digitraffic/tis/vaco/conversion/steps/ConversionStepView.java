package fi.digitraffic.tis.vaco.conversion.steps;

import javax.validation.constraints.NotNull;

public record ConversionStepView(@NotNull ConversionStepEnum step,
                                 @NotNull String stepSummary,
                                 @NotNull long timestamp) {
}


