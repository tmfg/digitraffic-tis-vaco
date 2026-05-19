package fi.digitraffic.tis.spikes.jacksonsubtype;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableSubtypeA.class)
@JsonDeserialize(builder = ImmutableSubtypeA.Builder.class)
public interface SubtypeA extends Subtype {
    @Nullable
    String subtypeValueA();
}
