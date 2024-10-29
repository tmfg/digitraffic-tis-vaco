package fi.digitraffic.tis.vaco.company.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DomainValue;
import org.immutables.value.Value;

@DomainValue
@Value.Immutable
@JsonSerialize(as = ImmutablePartnership.class)
@JsonDeserialize(as = ImmutablePartnership.class)
public interface Partnership {

    @Value.Parameter
    HierarchyType type();

    @Value.Parameter
    Company partnerA();

    @Value.Parameter
    Company partnerB();
}
