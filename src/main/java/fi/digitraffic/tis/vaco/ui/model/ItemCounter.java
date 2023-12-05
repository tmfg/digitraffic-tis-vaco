package fi.digitraffic.tis.vaco.ui.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableItemCounter.class)
@JsonDeserialize(as = ImmutableItemCounter.class)
public interface ItemCounter {
    @Value.Parameter
    String name();

    @Value.Parameter
    int total();
}
