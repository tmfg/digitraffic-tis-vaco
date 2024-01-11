package fi.digitraffic.tis.vaco.caching;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fi.digitraffic.tis.vaco.caching.mapper.CacheStatsMapper;
import fi.digitraffic.tis.vaco.caching.model.CacheSummaryStatistics;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Centralized caching control for entire application.
 *
 * If anything needs caching, pipe it through this service. This is especially important for invalidation to ensure all
 * dependent caches are also purged accordingly.
 */
@Service
public class CachingService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Cache<String, Ruleset> rulesetCache;
    private final Cache<String, String> sqsQueueUrlCache;
    private final Cache<Path, Path> localPathCache;
    private final CacheStatsMapper cacheStatsMapper;

    public CachingService(CacheStatsMapper cacheStatsMapper) {
        this.cacheStatsMapper = Objects.requireNonNull(cacheStatsMapper);
        this.rulesetCache = rulesetNameCache();
        this.sqsQueueUrlCache = sqsQueueUrlCache();
        this.localPathCache = localPathCache();
    }

    public Optional<Ruleset> cacheRuleset(String key, Function<String, Ruleset> loader) {
        return Optional.ofNullable(rulesetCache.get(key, loader));
    }

    public void invalidateRuleset(String key) {
        rulesetCache.invalidate(key);
    }

    public Optional<String> cacheQueueUrl(String key, UnaryOperator<String> loader) {
        return Optional.of(sqsQueueUrlCache.get(key, loader));
    }

    public void invalidateQueueUrl(String key) {
        sqsQueueUrlCache.invalidate(key);
    }

    public Optional<Path> cacheLocalTemporaryPath(Path key, UnaryOperator<Path> loader) {
        return Optional.ofNullable(localPathCache.get(key, loader));
    }

    public void invalidateLocalTemporaryPath(Path key) {
        localPathCache.invalidate(key);
    }

    private static Cache<String, Ruleset> rulesetNameCache() {
        return Caffeine.newBuilder()
            .recordStats()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofHours(1))
            .build();
    }

    private Cache<String, String> sqsQueueUrlCache() {
        return Caffeine.newBuilder()
            .recordStats()
            .maximumSize(50)
            .expireAfterWrite(Duration.ofHours(1))
            .build();
    }

    private Cache<Path, Path> localPathCache() {
        return Caffeine.newBuilder()
            .recordStats()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofDays(3))
            .evictionListener(((key, value, cause) -> {
                try {
                    if (key != null) {
                        Files.deleteIfExists((Path) key);
                    }
                } catch (IOException e) {
                    logger.error("Failed to delete file matching to evicted entry '{}' from packagesCache", key, e);
                }
            }))
            .build();
    }

    public Map<String, CacheSummaryStatistics> getStats() {
        return Map.of(
            "rulesets", cacheStatsMapper.toCacheSummaryStatistics(rulesetCache.stats()),
            "SQS queue URLs", cacheStatsMapper.toCacheSummaryStatistics(sqsQueueUrlCache.stats()),
            "local temporary file paths", cacheStatsMapper.toCacheSummaryStatistics(localPathCache.stats()));
    }
}
