package fi.digitraffic.tis.vaco;

import fi.digitraffic.tis.vaco.organization.model.CooperationType;
import fi.digitraffic.tis.vaco.organization.model.ImmutableCooperation;
import fi.digitraffic.tis.vaco.organization.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.validation.dto.ImmutableCooperationCommand;

import java.util.UUID;

public class TestObjects {

    // TODO: if this approach is good, move objects from older tests in here

    public static ImmutableOrganization.Builder anOrganization() {
        return ImmutableOrganization.builder()
            .businessId(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString());
    }

    public static ImmutableCooperation.Builder aCooperation() {
        return ImmutableCooperation.builder()
            .cooperationType(CooperationType.AUTHORITY_PROVIDER);
    }

    public static ImmutableCooperationCommand.Builder aCooperationDto() {
        return ImmutableCooperationCommand.builder()
            .cooperationType(CooperationType.AUTHORITY_PROVIDER);
    }
}
