package fi.digitraffic.tis.vaco.process.model;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.entries.model.Status;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.time.LocalDateTime;

@Value.Immutable
@JsonSerialize(as = ImmutableTask.class)
@JsonDeserialize(as = ImmutableTask.class)
@Value.Style(defaultAsDefault = true)
public interface Task {
    @Nullable
    @JsonView(DataVisibility.InternalOnly.class)
    Long id();

    @JsonView(DataVisibility.InternalOnly.class)
    @Value.Parameter
    Long entryId();

    @Nullable // TODO: make not nullable
    String publicId();

    @Value.Parameter
    String name();

    @Value.Parameter
    int priority();

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
