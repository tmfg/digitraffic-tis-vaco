package fi.digitraffic.tis.vaco.queuehandler.model;

import com.fasterxml.jackson.databind.JsonNode;
import fi.digitraffic.tis.vaco.entries.model.Status;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value.Immutable
public interface PersistentEntry {
    @Value.Parameter
    Long id();

    @Value.Parameter
    String publicId();

    @Value.Parameter
    String name();

    @Value.Parameter
    String format();

    @Value.Parameter
    String url();

    @Value.Parameter
    String businessId();

    @Nullable
    String etag();

    @Nullable
    JsonNode metadata();

    /**
     * List of email addresses to send notifications on events related to this entry, e.g. the matching job is complete.
     * @return List of emails as strings.
     */
    @Value.Default
    default List<String> notifications() {
        return List.of();
    }

    @Nullable
    LocalDateTime created();

    @Nullable
    LocalDateTime started();

    @Nullable
    LocalDateTime updated();

    @Nullable
    LocalDateTime completed();

    @Value.Default
    default Status status() {
        return Status.RECEIVED;
    }
}
