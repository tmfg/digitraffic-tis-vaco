package fi.digitraffic.tis.vaco.rules.gbfs;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.rules.RuleConfiguration;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableEnturGbfsValidatorConfiguration.class)
@JsonDeserialize(as = ImmutableEnturGbfsValidatorConfiguration.class)
public interface EnturGbfsValidatorConfiguration extends RuleConfiguration {
}
