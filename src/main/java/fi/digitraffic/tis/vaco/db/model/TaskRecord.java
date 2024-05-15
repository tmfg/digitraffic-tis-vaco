package fi.digitraffic.tis.vaco.db.model;

import fi.digitraffic.tis.vaco.entries.model.Status;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.time.ZonedDateTime;

@Value.Immutable
public interface TaskRecord {

    @Value.Parameter
    Long id();

    @Value.Parameter
    Long entryId();

    @Value.Parameter
    String publicId();

    @Value.Parameter
    String name();

    @Value.Parameter
    int priority();

    @Value.Parameter
    ZonedDateTime created();

    @Nullable
    ZonedDateTime started();

    @Nullable
    ZonedDateTime updated();

    @Nullable
    ZonedDateTime completed();

    @Value.Default
    default Status status() {
        return Status.RECEIVED;
    }
}

