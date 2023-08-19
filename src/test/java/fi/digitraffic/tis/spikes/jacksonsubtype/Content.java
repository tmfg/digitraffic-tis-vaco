package fi.digitraffic.tis.spikes.jacksonsubtype;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableContent.class)
@JsonDeserialize(as = ImmutableContent.class)
public interface Content {
    @Value.Parameter
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "name")
    @JsonSubTypes({
        @JsonSubTypes.Type(name = "a", value = SubtypeA.class),
        @JsonSubTypes.Type(name = "b", value = SubtypeB.class)
    })
    Subtype subtype();
}
