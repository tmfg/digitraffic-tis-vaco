package fi.digitraffic.tis.vaco.rules.model.netex;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableValidationReportEntry.class)
@JsonDeserialize(builder = ImmutableValidationReportEntry.Builder.class)
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
