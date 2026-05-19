package fi.digitraffic.tis.vaco.caching.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableLoadStatistics.class)
@JsonDeserialize(builder = ImmutableLoadStatistics.Builder.class)
public interface LoadStatistics {
    @Value.Parameter
    long loads();

    @Value.Parameter
    long successes();

    @Value.Parameter
    long failures();

    @Value.Default
    default double loadFailureRate() {
        return (loads() == 0) ? 0.0 : (double) failures() / loads();
    }

    @Value.Parameter
    long totalLoadTime();

}
