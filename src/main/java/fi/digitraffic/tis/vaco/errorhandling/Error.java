package fi.digitraffic.tis.vaco.errorhandling;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableError.class)
@JsonDeserialize(as = ImmutableError.class)
public interface Error {
    @Nullable
    @JsonView(DataVisibility.Internal.class)
    Long id();

    @Nullable
    String publicId();

    @Value.Parameter
    @Nullable
    @JsonView(DataVisibility.Internal.class)
    String entryId();

    @Value.Parameter
    @Nullable
    @JsonView(DataVisibility.Internal.class)
    Long taskId();

    @Value.Parameter
    @Nullable
    @JsonView(DataVisibility.Internal.class)
    Long rulesetId();

    @Value.Parameter
    String source();

    @Value.Parameter
    String message();

    @Value.Parameter
    @Value.Default
    default String severity() {
        return "UNKNOWN";
    }

    @Nullable
    byte[] raw();
}
