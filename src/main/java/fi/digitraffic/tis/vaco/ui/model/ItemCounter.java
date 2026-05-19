package fi.digitraffic.tis.vaco.ui.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableItemCounter.class)
@JsonDeserialize(builder = ImmutableItemCounter.Builder.class)
public interface ItemCounter {
    @Value.Parameter
    String name();

    @Value.Parameter
    long total();
}
