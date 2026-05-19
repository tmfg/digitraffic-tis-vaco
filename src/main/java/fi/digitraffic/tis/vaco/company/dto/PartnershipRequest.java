package fi.digitraffic.tis.vaco.company.dto;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutablePartnershipRequest.class)
@JsonDeserialize(builder = ImmutablePartnershipRequest.Builder.class)
@UniquePartners
public interface PartnershipRequest {
    @Value.Parameter
    String partnerABusinessId();

    @Value.Parameter
    String partnerBBusinessId();
}
