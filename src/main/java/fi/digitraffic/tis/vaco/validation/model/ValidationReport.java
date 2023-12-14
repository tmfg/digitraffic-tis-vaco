package fi.digitraffic.tis.vaco.validation.model;

import fi.digitraffic.tis.vaco.findings.Finding;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
public interface ValidationReport {

    @Value.Parameter
    String message();

    List<Finding> findings();
}
