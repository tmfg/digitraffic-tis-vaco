package fi.digitraffic.tis.vaco.organization.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableCooperation.class)
@JsonDeserialize(as = ImmutableCooperation.class)
public interface Cooperation {

    CooperationType cooperationType();

    Long partnerA();

    Long partnerB();
}