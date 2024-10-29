package fi.digitraffic.tis.vaco.api.model.hierarchies;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.api.model.refs.CompanyRef;
import fi.digitraffic.tis.vaco.company.model.HierarchyType;
import org.immutables.value.Value;

// TODO: Swagger doc
@Value.Immutable
@JsonSerialize(as = fi.digitraffic.tis.vaco.api.model.hierarchies.ImmutableCreateHierarchyRequest.class)
@JsonDeserialize(as = fi.digitraffic.tis.vaco.api.model.hierarchies.ImmutableCreateHierarchyRequest.class)
public interface CreateHierarchyRequest {

    @Value.Parameter
    HierarchyType type();

    @Value.Parameter
    CompanyRef company();

}
