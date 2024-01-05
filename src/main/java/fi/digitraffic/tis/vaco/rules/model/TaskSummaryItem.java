package fi.digitraffic.tis.vaco.rules.model;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableTaskSummaryItem.class)
@JsonDeserialize(as = ImmutableTaskSummaryItem.class)
public interface TaskSummaryItem {
    @Nullable
    @JsonView(DataVisibility.Internal.class)
    Long id();

    @Value.Parameter
    Long taskId();

    @Value.Parameter
    String name();

    @Nullable
    @Value.Parameter
    byte[] raw();
}
