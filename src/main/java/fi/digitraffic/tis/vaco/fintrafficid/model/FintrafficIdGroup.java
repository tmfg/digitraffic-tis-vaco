package fi.digitraffic.tis.vaco.fintrafficid.model;

import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.util.Optional;

@Value.Immutable
public interface FintrafficIdGroup {

    // This is MS Graph/AAD OID, not database id
    String id();

    @Nullable
    String displayName();

    @Nullable
    String description();

    Optional<OrganizationData> organizationData();
}
