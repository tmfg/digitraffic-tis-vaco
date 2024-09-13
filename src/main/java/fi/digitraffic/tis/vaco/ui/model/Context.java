package fi.digitraffic.tis.vaco.ui.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DomainValue;
import org.immutables.value.Value;

@DomainValue
@Value.Immutable
@JsonSerialize(as = ImmutableContext.class)
@JsonDeserialize(as = ImmutableContext.class)
public interface Context {
    @Value.Parameter
    String context();

    @Value.Parameter
    String businessId();
}
