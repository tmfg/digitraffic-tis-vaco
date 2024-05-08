package fi.digitraffic.tis.vaco.fintrafficid.model;

import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
public interface OrganizationData {

    @Nullable
    String phoneNumber();

    @Nullable
    String address();

    @Nullable
    String contactName();

    @Nullable
    String postalCode();

    @Nullable
    String municipality();

    @Nullable
    String businessId();

    @Nullable
    String name();

    @Nullable
    String contactPhoneNumber();

    @Nullable
    Boolean astraGovernmentOrganization();
}
