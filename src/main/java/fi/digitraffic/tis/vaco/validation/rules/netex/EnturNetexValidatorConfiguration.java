package fi.digitraffic.tis.vaco.validation.rules.netex;

import fi.digitraffic.tis.vaco.validation.rules.Configuration;
import org.immutables.value.Value;

import java.util.Set;

@Value.Immutable
public interface EnturNetexValidatorConfiguration extends Configuration {
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
