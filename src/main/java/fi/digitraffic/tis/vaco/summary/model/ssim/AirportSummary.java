package fi.digitraffic.tis.vaco.summary.model.ssim;

import org.immutables.value.Value;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@JsonSerialize(as = ImmutableAirportSummary.class)
@JsonDeserialize(as = ImmutableAirportSummary.class)
@Value.Style(jdk9Collections = true)
public interface AirportSummary {
    String name();
    String periodFrom();
    String periodTo();
    long inboundFlightsCount();
    long outboundFlightsCount();
}
