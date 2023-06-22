package fi.digitraffic.tis.vaco.conversion.model;

import fi.digitraffic.tis.vaco.errorhandling.Error;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
public interface ConversionReport {
    @Value.Parameter
    String message();

    List<Error> errors();
}
