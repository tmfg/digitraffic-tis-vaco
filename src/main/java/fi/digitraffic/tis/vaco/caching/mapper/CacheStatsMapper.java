package fi.digitraffic.tis.vaco.caching.mapper;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import fi.digitraffic.tis.vaco.caching.model.CacheSummaryStatistics;
import fi.digitraffic.tis.vaco.caching.model.ImmutableCacheSummaryStatistics;
import fi.digitraffic.tis.vaco.caching.model.ImmutableEvictionStatistics;
import fi.digitraffic.tis.vaco.caching.model.ImmutableLoadStatistics;
import fi.digitraffic.tis.vaco.caching.model.ImmutableRequestStatistics;
import org.springframework.stereotype.Component;

@Component
public class CacheStatsMapper {
    public <K, V> CacheSummaryStatistics toCacheSummaryStatistics(Cache<K, V> cache) {
        CacheStats stats = cache.stats();
        // Average load penalty (in nanoseconds) tracks how much time is "wasted" on loading. While interesting and also
        // related to loadFailureCount, it is not mapped because at the moment it is not really actionably interesting.
        // stats.averageLoadPenalty();

        // Eviction weight is Caffeine's internal concept of some object "weighing" more than others; it is not an
        // interesting generically summarizable statistic, which is why it is not mapped.
        //stats.evictionWeight();

        return ImmutableCacheSummaryStatistics.of(
            cache.policy().isRecordingStats(),
            cache.estimatedSize(),
            ImmutableRequestStatistics.of(
                stats.hitCount(),
                stats.missCount()),
            ImmutableLoadStatistics.of(
                stats.loadCount(),
                stats.loadSuccessCount(),
                stats.loadFailureCount(),
                stats.totalLoadTime()),
            ImmutableEvictionStatistics.of(stats.evictionCount())
        );
    }
}
