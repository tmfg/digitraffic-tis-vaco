package fi.digitraffic.tis.vaco.rules.model.gtfs2netex;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DomainValue;
import fi.digitraffic.tis.vaco.rules.RuleConfiguration;
import org.immutables.value.Value;

@DomainValue
@Value.Immutable
@JsonSerialize(as = ImmutableFintrafficGtfs2NetexConverterConfiguration.class)
@JsonDeserialize(as = ImmutableFintrafficGtfs2NetexConverterConfiguration.class)
public interface FintrafficGtfs2NetexConverterConfiguration extends RuleConfiguration {
}
