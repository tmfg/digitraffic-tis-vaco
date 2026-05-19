package fi.digitraffic.tis.vaco.caching.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableCacheSummaryStatistics.class)
@JsonDeserialize(builder = ImmutableCacheSummaryStatistics.Builder.class)
public interface CacheSummaryStatistics {

    @Value.Parameter
    boolean statisticsEnabled();

    @Value.Parameter
    long size();

    @Value.Parameter
    RequestStatistics requests();

    @Value.Parameter
    LoadStatistics loads();

    @Value.Parameter
    EvictionStatistics evictions();

    @Value.Parameter
    List<String> currentKeysInCache();
}
