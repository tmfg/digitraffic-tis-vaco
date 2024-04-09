package fi.digitraffic.tis.vaco.db.model;

import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import org.immutables.value.Value;

@Value.Immutable
public interface PartnershipRecord {
    @Value.Parameter
    PartnershipType type();

    @Value.Parameter
    Long partnerA();

    @Value.Parameter
    Long partnerB();
}
