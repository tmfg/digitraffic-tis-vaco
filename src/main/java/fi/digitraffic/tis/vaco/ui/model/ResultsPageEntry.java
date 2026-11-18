package fi.digitraffic.tis.vaco.ui.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * The entry fields that the results page shows. Other entry fields stay out of the response.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableResultsPageEntry.class)
@JsonDeserialize(as = ImmutableResultsPageEntry.class)
public interface ResultsPageEntry {
    String publicId();

    String name();

    String format();

    String url();

    @Nullable
    String etag();

    @Nullable
    String context();

    @Nullable
    ZonedDateTime created();

    @Nullable
    ZonedDateTime completed();

    List<ResultsPageTask> tasks();

    List<ResultsPageRule> validations();

    List<ResultsPageRule> conversions();
}
