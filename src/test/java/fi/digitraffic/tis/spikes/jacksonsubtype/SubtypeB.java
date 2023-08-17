package fi.digitraffic.tis.spikes.jacksonsubtype;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableSubtypeB.class)
@JsonDeserialize(as = ImmutableSubtypeB.class)
public interface SubtypeB extends Subtype {
    @Nullable
    abstract String subtypeValueB();
}
