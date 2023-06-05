package fi.digitraffic.tis.vaco.errorhandling;

import org.immutables.value.Value;

@Value.Immutable
public interface Error {
    @Value.Parameter
    String message();
}
