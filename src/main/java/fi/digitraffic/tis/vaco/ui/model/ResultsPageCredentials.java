package fi.digitraffic.tis.vaco.ui.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

/**
 * The credentials of an entry as the results page shows them. The page shows only the name.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableResultsPageCredentials.class)
@JsonDeserialize(as = ImmutableResultsPageCredentials.class)
public interface ResultsPageCredentials {
    String name();
}
