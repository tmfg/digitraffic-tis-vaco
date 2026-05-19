package fi.digitraffic.tis.vaco.rules.model.gbfs;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableGbfsError.class)
@JsonDeserialize(builder = ImmutableGbfsError.Builder.class)
public interface GbfsError {
    String schemaPath();
    String violationPath();
    String message();
}
