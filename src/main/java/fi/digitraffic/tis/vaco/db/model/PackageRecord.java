package fi.digitraffic.tis.vaco.db.model;

import org.immutables.value.Value;

@Value.Immutable
public interface PackageRecord {
    Long id();

    Long taskId();

    String name();

    String path();
}
