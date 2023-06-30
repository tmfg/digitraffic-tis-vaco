package fi.digitraffic.tis.vaco.organization.mapper;

import fi.digitraffic.tis.vaco.organization.model.CooperationType;
import fi.digitraffic.tis.vaco.organization.model.ImmutableCooperation;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CooperationCommandMapper {

    ImmutableCooperation toCooperation(CooperationType cooperationType,
                                       Long partnerA,
                                       Long partnerB);
}
