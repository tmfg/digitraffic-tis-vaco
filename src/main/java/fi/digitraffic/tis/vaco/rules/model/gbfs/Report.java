package fi.digitraffic.tis.vaco.rules.model.gbfs;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableReport.class)
@JsonDeserialize(as = ImmutableReport.class)
public interface Report {
    FileValidationResult fileValidationResult();
    String entry();
    List<Object> errors();
}
