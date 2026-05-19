package fi.digitraffic.tis.vaco.company.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DomainValue;
import org.immutables.value.Value;

@DomainValue
@Value.Immutable
@JsonSerialize(as = ImmutablePartnership.class)
@JsonDeserialize(builder = ImmutablePartnership.Builder.class)
public interface Partnership {

    @Value.Parameter
    PartnershipType type();

    @Value.Parameter
    Company partnerA();

    @Value.Parameter
    Company partnerB();
}
