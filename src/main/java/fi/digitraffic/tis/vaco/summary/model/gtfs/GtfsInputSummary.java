package fi.digitraffic.tis.vaco.summary.model.gtfs;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;
import java.util.Map;

@Value.Immutable
@JsonSerialize(as = ImmutableGtfsInputSummary.class)
@JsonDeserialize(as = ImmutableGtfsInputSummary.class)
@Value.Style(jdk9Collections = true)
public interface GtfsInputSummary {
    List<Map<String, String>> feedInfo();
    List<String> files();
    List<String> counts();
    List<String> components();
}
