package fi.digitraffic.tis.vaco.company.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableCooperation.class)
@JsonDeserialize(as = ImmutableCooperation.class)
public interface Cooperation {

    @Value.Parameter
    CooperationType cooperationType();

    @Value.Parameter
    Company partnerA();

    @Value.Parameter
    Company partnerB();
}
