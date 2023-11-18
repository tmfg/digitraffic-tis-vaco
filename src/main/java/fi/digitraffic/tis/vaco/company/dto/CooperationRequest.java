package fi.digitraffic.tis.vaco.company.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.company.model.CooperationType;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableCooperationRequest.class)
@JsonDeserialize(as = ImmutableCooperationRequest.class)
@UniquePartners
public interface CooperationRequest {
    CooperationType cooperationType();

    String partnerABusinessId();

    String partnerBBusinessId();
}
