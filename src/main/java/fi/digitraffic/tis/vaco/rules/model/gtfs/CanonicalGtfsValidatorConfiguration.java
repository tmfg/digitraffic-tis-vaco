package fi.digitraffic.tis.vaco.rules.model.gtfs;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.rules.RuleConfiguration;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableCanonicalGtfsValidatorConfiguration.class)
@JsonDeserialize(as = ImmutableCanonicalGtfsValidatorConfiguration.class)
public interface CanonicalGtfsValidatorConfiguration extends RuleConfiguration {}
