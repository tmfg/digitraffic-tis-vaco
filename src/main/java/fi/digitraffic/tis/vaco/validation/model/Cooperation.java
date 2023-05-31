package fi.digitraffic.tis.vaco.validation.model;

import org.immutables.value.Value;

@Value.Immutable
public interface Cooperation {

    CooperationType cooperationType();

    Long partnerA();

    Long partnerB();
}
