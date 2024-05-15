package fi.digitraffic.tis.vaco.process.model;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.entries.model.Status;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.time.ZonedDateTime;

@Value.Immutable
@JsonSerialize(as = ImmutableTask.class)
@JsonDeserialize(as = ImmutableTask.class)
@Value.Style(defaultAsDefault = true)
public interface Task {
    @Nullable
    @JsonView(DataVisibility.InternalOnly.class)
    Long id();

    @Nullable // TODO: make not nullable
    String publicId();

    @Value.Parameter
    String name();

    @Value.Parameter
    int priority();

    @Nullable
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
