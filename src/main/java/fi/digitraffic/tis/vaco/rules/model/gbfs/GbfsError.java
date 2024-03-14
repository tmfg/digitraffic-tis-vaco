package fi.digitraffic.tis.vaco.rules.model.gbfs;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableGbfsError.class)
@JsonDeserialize(as = ImmutableGbfsError.class)
public interface GbfsError {
    String schemaPath();
    String violationPath();
    String message();
}
