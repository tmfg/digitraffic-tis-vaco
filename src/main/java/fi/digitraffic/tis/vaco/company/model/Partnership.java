package fi.digitraffic.tis.vaco.company.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutablePartnership.class)
@JsonDeserialize(as = ImmutablePartnership.class)
public interface Partnership {

    @Value.Parameter
    PartnershipType type();

    @Value.Parameter
    Company partnerA();

    @Value.Parameter
    Company partnerB();
}
