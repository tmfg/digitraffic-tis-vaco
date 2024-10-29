package fi.digitraffic.tis.vaco.db.model;

import fi.digitraffic.tis.vaco.company.model.HierarchyType;
import org.immutables.value.Value;

@Value.Immutable
public interface PartnershipRecord {
    @Value.Parameter
    HierarchyType type();

    @Value.Parameter
    Long partnerA();

    @Value.Parameter
    Long partnerB();
}
