package fi.digitraffic.tis.vaco.rules.model.ssim2netex;

import fi.digitraffic.tis.vaco.DomainValue;
import fi.digitraffic.tis.vaco.rules.RuleConfiguration;
import org.immutables.value.Value;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

@DomainValue
@Value.Immutable
@JsonSerialize(as = ImmutableFintrafficSsim2NetexConverterConfiguration.class)
@JsonDeserialize(as = ImmutableFintrafficSsim2NetexConverterConfiguration.class)
public interface FintrafficSsim2NetexConverterConfiguration extends RuleConfiguration {
}
