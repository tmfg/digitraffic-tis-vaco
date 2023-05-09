package fi.digitraffic.tis.vaco.validation;

import java.util.List;

public record ValidationView(List<Error> errors) {
}
