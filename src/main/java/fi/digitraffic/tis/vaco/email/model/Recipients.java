package fi.digitraffic.tis.vaco.email.model;

import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
public interface Recipients {

    @Value.Default
    default List<String> to() {
        return List.of();
    }

    @Value.Default
    default List<String> cc() {
        return List.of();
    }

    @Value.Default
    default List<String> bcc() {
        return List.of();
    }
}
