package fi.digitraffic.tis.vaco.findings.model;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.DomainValue;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@DomainValue
@Value.Immutable
@JsonSerialize(as = ImmutableFinding.class)
@JsonDeserialize(as = ImmutableFinding.class)
public interface Finding {
    @Nullable
    @JsonView(DataVisibility.InternalOnly.class)
    Long id();

    @Nullable
    String publicId();

    @Value.Parameter
    @Nullable
    @JsonView(DataVisibility.InternalOnly.class)
    Long taskId();

    @Value.Parameter
    @Nullable
    @JsonView(DataVisibility.InternalOnly.class)
    Long rulesetId();

    @Value.Parameter
    String source();

    @Value.Parameter
    String message();

    @Value.Parameter
    default String severity() {
        return "UNKNOWN";
    }

    @Nullable
    byte[] raw();
}
