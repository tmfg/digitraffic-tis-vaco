package fi.digitraffic.tis.spikes.jacksonsubtype;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableSubtypeB.class)
@JsonDeserialize(as = ImmutableSubtypeB.class)
public interface SubtypeB extends Subtype {
    @Nullable
    String subtypeValueB();
}
