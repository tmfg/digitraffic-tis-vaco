package fi.digitraffic.tis.vaco.process.model;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.packages.model.Package;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableTask.class)
@JsonDeserialize(as = ImmutableTask.class)
@Value.Style(defaultAsDefault = true)
public interface Task {
    @Nullable
    @JsonView(DataVisibility.Internal.class)
    Long id();

    @JsonView(DataVisibility.Internal.class)
    @Value.Parameter
    Long entryId();

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

    @JsonView(DataVisibility.Internal.class)
    default List<Package> packages() {
        return List.of();
    }
}
