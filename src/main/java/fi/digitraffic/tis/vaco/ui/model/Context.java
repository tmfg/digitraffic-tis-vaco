package fi.digitraffic.tis.vaco.ui.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableContext.class)
@JsonDeserialize(as = ImmutableContext.class)
public interface Context {
    @Value.Parameter
    String context();

    @Value.Parameter
    String businessId();
}
