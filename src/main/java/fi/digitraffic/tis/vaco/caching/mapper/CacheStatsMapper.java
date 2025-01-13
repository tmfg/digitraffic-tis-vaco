package fi.digitraffic.tis.vaco.caching.mapper;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.caching.model.CacheSummaryStatistics;
import fi.digitraffic.tis.vaco.caching.model.ImmutableCacheSummaryStatistics;
import fi.digitraffic.tis.vaco.caching.model.ImmutableEvictionStatistics;
import fi.digitraffic.tis.vaco.caching.model.ImmutableLoadStatistics;
import fi.digitraffic.tis.vaco.caching.model.ImmutableRequestStatistics;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Component
public class CacheStatsMapper {
    public <K, V> CacheSummaryStatistics toCacheSummaryStatistics(Cache<K, V> cache) {
        CacheStats stats = cache.stats();
        /*
         stats.averageLoadPenalty() :: Average load penalty (in nanoseconds) tracks how much time is "wasted" on
         loading. While interesting and also related to loadFailureCount, it is not mapped because at the moment it is
         not really actionably interesting.

         stats.evictionWeight() :: Eviction weight is Caffeine's internal concept of some object "weighing" more than
         others; it is not an interesting generically summarizable statistic, which is why it is not mapped.
        */

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
            ImmutableEvictionStatistics.of(stats.evictionCount()),
            getKeySet(cache)
        );
    }

    private static <K, V> List<String> getKeySet(Cache<K, V> cache) {
        Set<K> cachedKeys = cache.asMap().keySet();
        List<String> sorted = new ArrayList<>(Streams.collect(cachedKeys, Object::toString));
        Collections.sort(sorted);
        return sorted;
    }
}
