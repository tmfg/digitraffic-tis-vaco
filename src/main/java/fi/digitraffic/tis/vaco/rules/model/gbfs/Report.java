package fi.digitraffic.tis.vaco.rules.model.gbfs;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableReport.class)
@JsonDeserialize(builder = ImmutableReport.Builder.class)
public interface Report {
    FileValidationResult fileValidationResult();
    String entry();
    List<Object> errors();
}
