package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.vaco.validation.steps.ValidationStatusEnum;

import java.util.List;

public record ValidationView(List<Error> errors,
                             ValidationStatusEnum status) {
}
