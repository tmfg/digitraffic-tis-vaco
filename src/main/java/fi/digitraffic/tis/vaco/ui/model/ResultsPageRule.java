package fi.digitraffic.tis.vaco.ui.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

/**
 * A validation or conversion rule as the results page shows it. The page shows only the name.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableResultsPageRule.class)
@JsonDeserialize(as = ImmutableResultsPageRule.class)
public interface ResultsPageRule {
    String name();
}
