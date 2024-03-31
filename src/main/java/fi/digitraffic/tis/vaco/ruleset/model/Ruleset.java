package fi.digitraffic.tis.vaco.ruleset.model;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import org.immutables.value.Value;

import java.util.Set;

// TODO: probably in wrong subsystem package
@Value.Immutable
@JsonSerialize(as = ImmutableRuleset.class)
@JsonDeserialize(as = ImmutableRuleset.class)
public interface Ruleset {
    @Nullable
    @JsonView(DataVisibility.InternalOnly.class)
    Long id();

    @Nullable
    String publicId();

    @Nullable
    @JsonView(DataVisibility.InternalOnly.class)
    @Value.Parameter
    Long ownerId();  // TODO: We might want to be able to show this with publicId in some cases

    @NotBlank
    @Value.Parameter
    String identifyingName();

    @NotBlank
    @Value.Parameter
    String description();

    @NotBlank
    @Value.Parameter
    Category category();

    @NotBlank
    @Value.Parameter
    Type type();

    @NotBlank
    @Value.Parameter
    TransitDataFormat format();

    @Value.Default
    default Set<String> dependencies() {
        return Set.of();
    }
}
