package fi.digitraffic.tis.vaco.company.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.Set;

@Value.Immutable
@JsonSerialize(as = ImmutableHierarchy.class)
@JsonDeserialize(as = ImmutableHierarchy.class)
public interface Hierarchy {

    Company company();

    @Value.Default
    default Set<Hierarchy> children() {
        return Set.of();
    }
}
