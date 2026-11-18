package fi.digitraffic.tis.vaco.ui.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

/**
 * A single finding as the results page shows it. The page reads only the raw content.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableResultsPageFinding.class)
@JsonDeserialize(as = ImmutableResultsPageFinding.class)
public interface ResultsPageFinding {
    @Nullable
    byte[] raw();
}
