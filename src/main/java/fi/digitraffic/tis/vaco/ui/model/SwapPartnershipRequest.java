package fi.digitraffic.tis.vaco.ui.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableSwapPartnershipRequest.class)
@JsonDeserialize(as = ImmutableSwapPartnershipRequest.class)
@UniquePartners
public interface SwapPartnershipRequest {
    String oldPartnerABusinessId();

    String newPartnerABusinessId();

    String partnerBBusinessId();
}
