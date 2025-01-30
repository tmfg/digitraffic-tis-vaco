package fi.digitraffic.tis.vaco.queuehandler.model;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.DomainValue;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.model.Task;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.time.ZonedDateTime;
import java.util.List;

@DomainValue
@Value.Immutable
@JsonSerialize(as = ImmutableEntry.class)
@JsonDeserialize(as = ImmutableEntry.class)
public interface Entry {
    /**
     * Temporary identifier to use when mapping between types in contexts where the public id is not available otherwise.
     * Use with extreme caution and preferably not at all unless you really, really, REALLY have to!
     */
    String NON_PERSISTED_PUBLIC_ID = "<< !!! NON-PERSISTED ENTRY !!! >>";

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

    @Nullable
    List<Task> tasks();

    @Nullable
    List<ValidationInput> validations();

    @Nullable
    List<ConversionInput> conversions();

    @Nullable
    @JsonView({DataVisibility.InternalOnly.class})
    List<Package> packages();

    /**
     * List of email addresses to send notifications on events related to this entry, e.g. the matching job is complete.
     * @return List of emails as strings.
     */
    @JsonView(DataVisibility.AdminRestricted.class)
    default List<String> notifications() {
        return List.of();
    }

    @Nullable
    ZonedDateTime created();

    @Nullable
    ZonedDateTime started();

    @Nullable
    ZonedDateTime updated();

    @Nullable
    ZonedDateTime completed();

    default Status status() {
        return Status.RECEIVED;
    }

    @Nullable
    String context();

    @Nullable
    String credentials();

    @Value.Parameter
    default boolean sendNotifications() { return true; }
}
