package fi.digitraffic.tis.vaco.validation.model;

import org.immutables.value.Value;

@Value.Immutable
public interface ValidationReport {
    @Value.Parameter
    String stageName();
    @Value.Parameter
    String message();
}
