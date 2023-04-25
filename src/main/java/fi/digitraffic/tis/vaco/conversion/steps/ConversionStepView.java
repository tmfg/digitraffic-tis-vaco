package fi.digitraffic.tis.vaco.conversion.steps;

import javax.validation.constraints.NotNull;

public record ConversionStepView(@NotNull ConversionPhaseEnum phase,
                                 @NotNull String phaseSummary,
                                 @NotNull long timestamp) {
}


