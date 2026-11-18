package fi.digitraffic.tis.vaco.ui.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.entries.model.Status;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.time.ZonedDateTime;

/**
 * A task as the results page shows it.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableResultsPageTask.class)
@JsonDeserialize(as = ImmutableResultsPageTask.class)
public interface ResultsPageTask {
    String name();

    Status status();

    @Nullable
    ZonedDateTime completed();
}
