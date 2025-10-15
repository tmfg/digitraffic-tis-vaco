package fi.digitraffic.tis.vaco.rules.model.netex2gtfs;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DomainValue;
import fi.digitraffic.tis.vaco.rules.RuleConfiguration;
import org.immutables.value.Value;

@DomainValue
@Value.Immutable
@JsonSerialize(as = ImmutableEnturNetex2GtfsConverterConfiguration.class)
@JsonDeserialize(as = ImmutableEnturNetex2GtfsConverterConfiguration.class)
public interface EnturNetex2GtfsConverterConfiguration extends RuleConfiguration {
    String codespace();

    default boolean stopsOnly() {
        return false;
    }
}
