package fi.digitraffic.tis.vaco.summary.model.ssim;

import org.immutables.value.Value;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableAirlineSummary.class)
@JsonDeserialize(as = ImmutableAirlineSummary.class)
@Value.Style(jdk9Collections = true)
public interface AirlineSummary {
    String name();
    String periodFrom();
    String periodTo();
    long flightCount();
    List<String> airports();
}
