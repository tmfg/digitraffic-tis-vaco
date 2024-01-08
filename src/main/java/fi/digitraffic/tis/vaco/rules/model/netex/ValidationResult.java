package fi.digitraffic.tis.vaco.rules.model.netex;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableValidationResult.class)
@JsonDeserialize(as = ImmutableValidationResult.class)
public interface ValidationResult {
    ValidationReport validationReport();
    String entry();
    List<Object> errors();
}
