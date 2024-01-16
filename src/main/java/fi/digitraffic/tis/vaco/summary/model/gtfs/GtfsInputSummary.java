package fi.digitraffic.tis.vaco.summary.model.gtfs;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableGtfsInputSummary.class)
@JsonDeserialize(as = ImmutableGtfsInputSummary.class)
@Value.Style(jdk9Collections = true)
public interface GtfsInputSummary {
    List<Agency> agencies();
    FeedInfo feedInfo();
    List<String> files();
    List<String> counts();
    List<String> components();
}
