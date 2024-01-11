package fi.digitraffic.tis.vaco.caching.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableCacheSummaryStatistics.class)
@JsonDeserialize(as = ImmutableCacheSummaryStatistics.class)
public interface CacheSummaryStatistics {

    @Value.Parameter
    RequestStatistics requests();

    @Value.Parameter
    LoadStatistics loads();

    @Value.Parameter
    EvictionStatistics evictions();

}
