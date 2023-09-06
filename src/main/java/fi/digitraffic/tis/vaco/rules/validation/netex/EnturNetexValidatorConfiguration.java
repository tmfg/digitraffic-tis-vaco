package fi.digitraffic.tis.vaco.rules.validation.netex;

import fi.digitraffic.tis.vaco.rules.validation.ValidatorConfiguration;
import org.immutables.value.Value;

import java.util.Set;

@Value.Immutable
public interface EnturNetexValidatorConfiguration extends ValidatorConfiguration {
    EnturNetexValidatorConfiguration DEFAULTS = ImmutableEnturNetexValidatorConfiguration.builder()
        .codespace("FIN")
        .reportId("NO_REPORT_ID_PROVIDED")
        .addIgnorableNetexElements("SiteFrame")
        .maximumErrors(128)
        .build();

    String codespace();
    String reportId();
    Set<String> ignorableNetexElements();
    int maximumErrors();

}
