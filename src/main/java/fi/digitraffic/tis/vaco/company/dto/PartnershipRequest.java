package fi.digitraffic.tis.vaco.company.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutablePartnershipRequest.class)
@JsonDeserialize(as = ImmutablePartnershipRequest.class)
@UniquePartners
public interface PartnershipRequest {
    @Value.Parameter
    String partnerABusinessId();

    @Value.Parameter
    String partnerBBusinessId();
}
