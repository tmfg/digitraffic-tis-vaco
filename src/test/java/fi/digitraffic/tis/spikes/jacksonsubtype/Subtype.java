package fi.digitraffic.tis.spikes.jacksonsubtype;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableSubtype.class)
@JsonDeserialize(as = ImmutableSubtype.class)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "name")
@JsonSubTypes({
    @JsonSubTypes.Type(name = "a", value = SubtypeA.class),
    @JsonSubTypes.Type(name = "b", value = SubtypeB.class)
})
public interface Subtype {
    @Nullable
    abstract String getName();
}
