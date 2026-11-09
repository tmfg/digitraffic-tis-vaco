package fi.digitraffic.tis.vaco.ui.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableAggregatedFinding.class)
@JsonDeserialize(as = ImmutableAggregatedFinding.class)
public interface AggregatedFinding {

    String code();

    String severity();

    int total();

    @Nullable
    List<ResultsPageFinding> findings();
}
