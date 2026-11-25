package fi.digitraffic.tis.vaco.rules.model.gbfs;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.Optional;

@Value.Immutable
@JsonSerialize(as = ImmutableGbfsError.class)
@JsonDeserialize(as = ImmutableGbfsError.class)
public interface GbfsError {
    Optional<String> schemaPath();
    String violationPath();
    String message();
}
