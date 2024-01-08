package fi.digitraffic.tis.vaco.rules.model.netex;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Value.Immutable
@JsonSerialize(as = ImmutableValidationReport.class)
@JsonDeserialize(as = ImmutableValidationReport.class)
public interface ValidationReport {
    @Nullable
    String codespace();
    @Nullable
    String validationReportId();
    @Nullable
    LocalDateTime creationDate();
    Map<String, Long> numberOfValidationEntriesPerRule();
    List<ValidationReportEntry> validationReportEntries();
}
