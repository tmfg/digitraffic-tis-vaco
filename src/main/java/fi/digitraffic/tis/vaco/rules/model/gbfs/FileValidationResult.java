package fi.digitraffic.tis.vaco.rules.model.gbfs;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableFileValidationResult.class)
@JsonDeserialize(builder = ImmutableFileValidationResult.Builder.class)
public interface FileValidationResult {
    String file();
    boolean required();
    boolean exists();
    int errorsCount();
    /*
     these properties exist but we do not use them
     Json schema();
     Json fileContents();
    */
    String version();
    List<GbfsError> errors();
}
