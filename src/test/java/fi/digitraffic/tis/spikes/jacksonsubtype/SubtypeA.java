package fi.digitraffic.tis.spikes.jacksonsubtype;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableSubtypeA.class)
@JsonDeserialize(as = ImmutableSubtypeA.class)
public interface SubtypeA extends Subtype {
    @Nullable
    abstract String subtypeValueA();
}
