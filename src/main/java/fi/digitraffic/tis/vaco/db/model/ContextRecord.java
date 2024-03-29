package fi.digitraffic.tis.vaco.db.model;

import org.immutables.value.Value;

@Value.Immutable
public interface ContextRecord {
    @Value.Parameter
    Long id();

    @Value.Parameter
    Long companyId();

    @Value.Parameter
    String context();
}
