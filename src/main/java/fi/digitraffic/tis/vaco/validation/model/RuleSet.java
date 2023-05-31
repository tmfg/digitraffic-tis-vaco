package fi.digitraffic.tis.vaco.validation.model;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import org.immutables.value.Value;

// TODO: probably in wrong subsystem package
@Value.Immutable
@JsonSerialize(as = ImmutableRuleSet.class)
@JsonDeserialize(as = ImmutableRuleSet.class)
public interface RuleSet {
    @Nullable
    @JsonView(DataVisibility.Internal.class)
    Long id();

    @Nullable
    String publicId();

    @Nullable
    @JsonView(DataVisibility.Internal.class)
    Long ownerId();  // TODO: We might want to be able to show this with publicId in some cases

    @NotBlank
    String identifyingName();

    @NotBlank
    String description();

    @NotBlank
    Category category();
}
