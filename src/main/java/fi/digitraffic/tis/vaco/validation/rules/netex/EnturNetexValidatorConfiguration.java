package fi.digitraffic.tis.vaco.validation.rules.netex;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Set;

public class EnturNetexValidatorConfiguration {
    public static final EnturNetexValidatorConfiguration DEFAULTS = new EnturNetexValidatorConfiguration(
        "FIN", "NO_REPORT_ID_PROVIDED", Set.of("SiteFrame"), 64 * 1024
    );

    private final String codespace;
    private final String reportId;
    private final Set<String> ignorableNetexElements;
    private final int maximumErrors;

    @JsonCreator
    public EnturNetexValidatorConfiguration(String codespace, String reportId, Set<String> ignorableNetexElements, int maximumErrors) {
        this.codespace = codespace;
        this.reportId = reportId;
        this.ignorableNetexElements = ignorableNetexElements;
        this.maximumErrors = maximumErrors;
    }

    public String getCodespace() {
        return codespace;
    }

    public String getReportId() {
        return reportId;
    }

    public Set<String> getIgnorableNetexElements() {
        return ignorableNetexElements;
    }

    public int getMaximumErrors() {
        return maximumErrors;
    }
}
