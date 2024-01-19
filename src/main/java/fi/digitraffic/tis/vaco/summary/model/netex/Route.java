package fi.digitraffic.tis.vaco.summary.model.netex;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableRoute.class)
@JsonDeserialize(as = ImmutableRoute.class)
public interface Route {
    String id();
    String lineRef();
}
