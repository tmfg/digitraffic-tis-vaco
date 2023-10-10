package fi.digitraffic.tis.vaco.queuehandler.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.rules.RuleConfiguration;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableValidationInput.class)
@JsonDeserialize(as = ImmutableValidationInput.class)
public interface ValidationInput {
    @Nullable
    @JsonView(DataVisibility.Internal.class)
    Long id();

    @Value.Parameter
    String name();

    @Nullable
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "name")
    RuleConfiguration config();
}
