package fi.digitraffic.tis.vaco.rules.model.ssim;

import fi.digitraffic.tis.vaco.DomainValue;
import fi.digitraffic.tis.vaco.rules.RuleConfiguration;
import org.immutables.value.Value;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

@DomainValue
@Value.Immutable
@JsonSerialize(as = ImmutableFintrafficSsimValidatorConfiguration.class)
@JsonDeserialize(as = ImmutableFintrafficSsimValidatorConfiguration.class)
public interface FintrafficSsimValidatorConfiguration extends RuleConfiguration {
}
