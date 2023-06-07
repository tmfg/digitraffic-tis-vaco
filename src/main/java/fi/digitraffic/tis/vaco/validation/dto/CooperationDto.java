package fi.digitraffic.tis.vaco.validation.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.tis.model.CooperationType;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableCooperationDto.class)
@JsonDeserialize(as = ImmutableCooperationDto.class)
public interface CooperationDto {
    CooperationType cooperationType();

    String partnerABusinessId();

    String partnerBBusinessId();
}
