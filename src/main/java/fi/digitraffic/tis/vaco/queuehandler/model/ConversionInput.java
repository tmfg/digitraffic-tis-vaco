package fi.digitraffic.tis.vaco.queuehandler.model;

import com.fasterxml.jackson.annotation.JsonView;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.DomainValue;
import fi.digitraffic.tis.vaco.rules.RuleConfiguration;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@DomainValue
@Value.Immutable
@JsonSerialize(as = ImmutableConversionInput.class)
@JsonDeserialize(as = ImmutableConversionInput.class)
public interface ConversionInput {
    @Nullable
    @JsonView(DataVisibility.InternalOnly.class)
    Long id();

    @Value.Parameter
    String name();

    @Nullable
    RuleConfiguration config();
}
