package fi.digitraffic.tis.vaco;

import fi.digitraffic.tis.vaco.tis.model.CooperationType;
import fi.digitraffic.tis.vaco.tis.model.ImmutableCooperation;
import fi.digitraffic.tis.vaco.tis.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.validation.dto.ImmutableCooperationDto;

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

    public static ImmutableCooperationDto.Builder aCooperationDto() {
        return ImmutableCooperationDto.builder()
            .cooperationType(CooperationType.AUTHORITY_PROVIDER);
    }
}
