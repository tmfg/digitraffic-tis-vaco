package fi.digitraffic.tis.vaco.organization.model;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableOrganization.class)
@JsonDeserialize(as = ImmutableOrganization.class)
public interface Organization {

    @Nullable
    @JsonView(DataVisibility.Internal.class)
    Long id();

    // TODO: think about removing publicId
    @Nullable
    @Value.Parameter
    String publicId();

    @Value.Parameter
    String businessId();

    @Value.Parameter
    String name();
}
