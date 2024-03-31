package fi.digitraffic.tis.vaco.admintasks.model;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.time.LocalDateTime;

@Value.Immutable
@JsonSerialize(as = ImmutableGroupIdMappingTask.class)
@JsonDeserialize(as = ImmutableGroupIdMappingTask.class)
public interface GroupIdMappingTask {

    @Nullable
    @JsonView(DataVisibility.InternalOnly.class)
    Long id();

    @Nullable
    String publicId();

    @Value.Parameter
    String groupId();

    @Value.Default
    default boolean skip() {
        return false;
    }

    @Nullable
    LocalDateTime created();

    @Nullable
    LocalDateTime completed();

    @Nullable
    String completedBy();
}
