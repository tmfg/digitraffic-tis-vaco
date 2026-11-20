package fi.digitraffic.tis.vaco.summary.model.ssim;

import org.immutables.value.Value;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.util.List;
import java.util.Map;

@Value.Immutable
@JsonSerialize(as = ImmutableSsimSummary.class)
@JsonDeserialize(as = ImmutableSsimSummary.class)
@Value.Style(jdk9Collections = true)
public interface SsimSummary {
    Map<String, String> feedInfo();
    List<AirlineSummary> airlines();
    List<AirportSummary> airports();
    List<String> counts();
}
