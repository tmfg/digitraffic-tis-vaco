package fi.digitraffic.tis.vaco.caching.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableRequestStatistics.class)
@JsonDeserialize(as = ImmutableRequestStatistics.class)
public interface RequestStatistics {
    @Value.Parameter
    long hits();

    @Value.Parameter
    long misses();

    @Value.Default
    default long total() {
        return hits() + misses();
    }

    @Value.Default
    default double hitRate() {
        return (total() == 0) ? 1.0 : (double) hits() / total();
    }

    @Value.Default
    default double missRate() {
        return (total() == 0) ? 0.0 : (double) misses() / total();
    }
}
