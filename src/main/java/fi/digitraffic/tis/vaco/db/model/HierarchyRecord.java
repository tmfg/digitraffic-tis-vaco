package fi.digitraffic.tis.vaco.db.model;

import fi.digitraffic.tis.vaco.company.model.HierarchyType;
import org.immutables.value.Value;

@Value.Immutable
public interface HierarchyRecord {
    long id();

    String publicId();

    long rootCompanyId();

    HierarchyType type();
}
