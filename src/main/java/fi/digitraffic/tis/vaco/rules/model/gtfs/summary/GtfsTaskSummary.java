package fi.digitraffic.tis.vaco.rules.model.gtfs.summary;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.rules.model.TaskSummary;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableGtfsTaskSummary.class)
@JsonDeserialize(as = ImmutableGtfsTaskSummary.class)
@Value.Style(jdk9Collections = true)
public interface GtfsTaskSummary extends TaskSummary {
    List<Agency> agencies();
    FeedInfo feedInfo();
}
