package fi.digitraffic.tis.vaco.caching.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableEvictionStatistics.class)
@JsonDeserialize(as = ImmutableEvictionStatistics.class)
public interface EvictionStatistics {

    @Value.Parameter
    long evictions();
}
