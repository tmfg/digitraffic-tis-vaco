package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.vaco.validation.steps.ValidationStatus;

import java.util.List;

public record ValidationView(List<Error> errors,
                             ValidationStatus status) {
}
