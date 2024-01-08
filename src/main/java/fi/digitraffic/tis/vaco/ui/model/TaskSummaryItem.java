package fi.digitraffic.tis.vaco.ui.model;

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
    @JsonView(DataVisibility.Internal.class)
    Long taskId();

    String title();

    String type();

    @Nullable
    Object content();
}
