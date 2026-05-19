package fi.digitraffic.tis.vaco.rules.model.netex;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableValidationResult.class)
@JsonDeserialize(builder = ImmutableValidationResult.Builder.class)
public interface ValidationResult {
    ValidationReport validationReport();
    String entry();
    List<Object> errors();
}
