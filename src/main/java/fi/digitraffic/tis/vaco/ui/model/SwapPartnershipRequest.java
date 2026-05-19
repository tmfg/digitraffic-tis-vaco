package fi.digitraffic.tis.vaco.ui.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableSwapPartnershipRequest.class)
@JsonDeserialize(builder = ImmutableSwapPartnershipRequest.Builder.class)
@UniquePartners
public interface SwapPartnershipRequest {
    String oldPartnerABusinessId();

    String newPartnerABusinessId();

    String partnerBBusinessId();
}
