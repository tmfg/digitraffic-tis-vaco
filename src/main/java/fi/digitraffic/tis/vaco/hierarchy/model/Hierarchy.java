package fi.digitraffic.tis.vaco.hierarchy.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.HierarchyType;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = fi.digitraffic.tis.vaco.hierarchy.model.ImmutableHierarchy.class)
@JsonDeserialize(as = fi.digitraffic.tis.vaco.hierarchy.model.ImmutableHierarchy.class)
public interface Hierarchy {

    HierarchyType type();

    Company root();
}
