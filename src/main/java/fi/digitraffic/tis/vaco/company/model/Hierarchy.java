package fi.digitraffic.tis.vaco.company.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DomainValue;
import org.immutables.value.Value;

import java.util.Set;

@DomainValue
@Value.Immutable
@JsonSerialize(as = ImmutableHierarchy.class)
@JsonDeserialize(builder = ImmutableHierarchy.Builder.class)
public interface Hierarchy {

    Company company();

    default Set<Hierarchy> children() {
        return Set.of();
    }
}
