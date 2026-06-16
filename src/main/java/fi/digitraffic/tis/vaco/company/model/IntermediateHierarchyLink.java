package fi.digitraffic.tis.vaco.company.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableIntermediateHierarchyLink.class)
@JsonDeserialize(as = ImmutableIntermediateHierarchyLink.class)
public interface IntermediateHierarchyLink {
    @Nullable
    Long parentId();

    @Nullable
    Long childId();
}
