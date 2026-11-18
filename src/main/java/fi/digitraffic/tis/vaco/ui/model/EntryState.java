package fi.digitraffic.tis.vaco.ui.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.summary.model.Summary;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableEntryState.class)
@JsonDeserialize(as = ImmutableEntryState.class)
public interface EntryState {

    Resource<ResultsPageEntry> entry();

    @Value.Default
    default List<Summary> summaries() {
        return List.of();
    }

    @Value.Default
    default List<TaskReport> reports() {
        return List.of();
    }

    String company();

    @Nullable
    ResultsPageCredentials credentials();
}
