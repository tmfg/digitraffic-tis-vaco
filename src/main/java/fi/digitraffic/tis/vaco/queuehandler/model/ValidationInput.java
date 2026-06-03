package fi.digitraffic.tis.vaco.queuehandler.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DomainValue;
import fi.digitraffic.tis.vaco.rules.RuleConfiguration;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@DomainValue
@Value.Immutable
@JsonSerialize(as = ImmutableValidationInput.class)
@JsonDeserialize(builder = ImmutableValidationInput.Builder.class)
public interface ValidationInput {
    @Value.Parameter
    String name();

    @Nullable
    RuleConfiguration config();
}
