package fi.digitraffic.tis.vaco.rules.model.netex2gtfs;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.rules.RuleConfiguration;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableEnturNetex2GtfsConverterConfiguration.class)
@JsonDeserialize(as = ImmutableEnturNetex2GtfsConverterConfiguration.class)
public interface EnturNetex2GtfsConverterConfiguration extends RuleConfiguration {
    String namespace();
}
