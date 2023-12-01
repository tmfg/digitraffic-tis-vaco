package fi.digitraffic.tis.vaco.queuehandler.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.errorhandling.Error;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.model.Task;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableEntry.class)
@JsonDeserialize(as = ImmutableEntry.class)
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public interface Entry {
    @Nullable
    @JsonView(DataVisibility.Internal.class)
    Long id();

    @Nullable
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
    @JsonView(DataVisibility.Internal.class)
    List<Package> packages();

    @Nullable
    List<Error> errors();

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
}
