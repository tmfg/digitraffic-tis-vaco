package fi.digitraffic.tis.vaco.company.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutablePartnershipRequest.class)
@JsonDeserialize(as = ImmutablePartnershipRequest.class)
@UniquePartners
public interface PartnershipRequest {
    PartnershipType type();

    String partnerABusinessId();

    String partnerBBusinessId();
}
