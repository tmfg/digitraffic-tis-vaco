package fi.digitraffic.tis.vaco.rules.validation.netex;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.rules.RuleConfiguration;
import org.immutables.value.Value;

import java.util.Set;

@Value.Immutable
@JsonSerialize(as = ImmutableEnturNetexValidatorConfiguration.class)
@JsonDeserialize(as = ImmutableEnturNetexValidatorConfiguration.class)
public interface EnturNetexValidatorConfiguration extends RuleConfiguration {
    String codespace();
    String reportId();
    Set<String> ignorableNetexElements();
    int maximumErrors();
}
