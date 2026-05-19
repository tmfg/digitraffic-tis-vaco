package fi.digitraffic.tis.vaco.rules.model.gtfs;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DomainValue;
import fi.digitraffic.tis.vaco.rules.RuleConfiguration;
import org.immutables.value.Value;

@DomainValue
@Value.Immutable
@JsonSerialize(as = ImmutableCanonicalGtfsValidatorConfiguration.class)
@JsonDeserialize(builder = ImmutableCanonicalGtfsValidatorConfiguration.Builder.class)
public interface CanonicalGtfsValidatorConfiguration extends RuleConfiguration {}
