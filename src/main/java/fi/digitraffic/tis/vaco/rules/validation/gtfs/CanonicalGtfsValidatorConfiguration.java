package fi.digitraffic.tis.vaco.rules.validation.gtfs;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.rules.validation.ValidatorConfiguration;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableCanonicalGtfsValidatorConfiguration.class)
@JsonDeserialize(as = ImmutableCanonicalGtfsValidatorConfiguration.class)
public interface CanonicalGtfsValidatorConfiguration extends ValidatorConfiguration {

    @Nullable
    String lol();

}