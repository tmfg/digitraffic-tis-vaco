package fi.digitraffic.tis.vaco.rules.model.netex;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableValidationReportEntry.class)
@JsonDeserialize(as = ImmutableValidationReportEntry.class)
public interface ValidationReportEntry {
    String name();

    String message();

    String severity();

    @Nullable
    String objectId();

    String fileName();

    int lineNumber();

    int columnNumber();
}
