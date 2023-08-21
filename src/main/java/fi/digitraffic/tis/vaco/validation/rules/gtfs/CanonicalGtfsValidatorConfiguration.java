package fi.digitraffic.tis.vaco.validation.rules.gtfs;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.validation.rules.Configuration;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableCanonicalGtfsValidatorConfiguration.class)
@JsonDeserialize(as = ImmutableCanonicalGtfsValidatorConfiguration.class)
public interface CanonicalGtfsValidatorConfiguration extends Configuration {

    @Nullable
    String lol();

}
