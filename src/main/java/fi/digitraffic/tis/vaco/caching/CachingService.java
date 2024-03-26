package fi.digitraffic.tis.vaco.caching;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fi.digitraffic.tis.vaco.admintasks.model.GroupIdMappingTask;
import fi.digitraffic.tis.vaco.caching.mapper.CacheStatsMapper;
import fi.digitraffic.tis.vaco.caching.model.CacheSummaryStatistics;
import fi.digitraffic.tis.vaco.db.model.ContextRecord;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
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
 * <p>
 * If anything needs caching, pipe it through this service. This is especially important for invalidation to ensure all
 * dependent caches are also purged accordingly.
 */
@Service
public class CachingService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Cache<String, GroupIdMappingTask> adminTasksCache;
    private final Cache<String, Ruleset> rulesetCache;
    private final Cache<String, String> sqsQueueUrlCache;
    private final Cache<Path, Path> localPathCache;
    private final Cache<String, Entry> entryCache;
    private final Cache<String, Status> statusCache;
    private final Cache<String, ClassPathResource> classPathResourceCache;
    private final CacheStatsMapper cacheStatsMapper;

    // *Record caches are database specific and should only be accesssed from *Repositories
    private final Cache<String, ContextRecord> contextRecordCache;

    public CachingService(CacheStatsMapper cacheStatsMapper) {
        this.cacheStatsMapper = Objects.requireNonNull(cacheStatsMapper);
        this.adminTasksCache = genericCache(100);
        this.rulesetCache = genericCache(500);
        this.sqsQueueUrlCache = sqsQueueUrlCache();
        this.localPathCache = localPathCache();
        this.entryCache = genericCache(500);
        this.statusCache = genericCache(3000);
        this.classPathResourceCache = genericCache(Status.values().length);
        this.contextRecordCache = genericCache(300);
    }

    public Optional<Ruleset> cacheRuleset(String key, Function<String, Ruleset> loader) {
        return Optional.ofNullable(rulesetCache.get(key, loader));
    }

    public void invalidateRuleset(String key) {
        rulesetCache.invalidate(key);
    }

    public Optional<String> cacheQueueUrl(String key, UnaryOperator<String> loader) {
        return Optional.ofNullable(sqsQueueUrlCache.get(key, loader));
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

    public Optional<Entry> cacheEntry(String key, Function<String, Entry> loader) {
        return Optional.ofNullable(entryCache.get(key, loader));
    }

    public void invalidateEntry(String publicId) {
        entryCache.invalidate(publicId);
        invalidateStatus(publicId);
    }

    public GroupIdMappingTask cacheAdminTask(String key, Function<String, GroupIdMappingTask> loader) {
        return adminTasksCache.get(key, loader);
    }

    public void invalidateAdminTask(String key) {
        adminTasksCache.invalidate(key);
    }

    public GroupIdMappingTask forceUpdateAdminTask(String key, Function<String, GroupIdMappingTask> loader) {
        GroupIdMappingTask result = loader.apply(key);
        adminTasksCache.put(key, result);
        return result;
    }

    public Optional<Status> cacheStatus(String key, Function<String, Status> loader) {
        return Optional.ofNullable(statusCache.get(key, loader));
    }

    public void invalidateStatus(String key) {
        statusCache.invalidate(key);
    }

    public Optional<ClassPathResource> cacheClassPathResource(String key, Function<String, ClassPathResource> loader) {
        return Optional.ofNullable(classPathResourceCache.get(key, loader));
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

    private <K, V> Cache<K, V> genericCache(int size) {
        return Caffeine.newBuilder()
            .recordStats()
            .maximumSize(size)
            .expireAfterWrite(Duration.ofDays(1))
            .build();
    }

    public Map<String, CacheSummaryStatistics> getStats() {
        return Map.of(
            "rulesets", cacheStatsMapper.toCacheSummaryStatistics(rulesetCache),
            "SQS queue URLs", cacheStatsMapper.toCacheSummaryStatistics(sqsQueueUrlCache),
            "local temporary file paths", cacheStatsMapper.toCacheSummaryStatistics(localPathCache),
            "entries", cacheStatsMapper.toCacheSummaryStatistics(entryCache),
            "statuses", cacheStatsMapper.toCacheSummaryStatistics(classPathResourceCache),
            "classpath resources", cacheStatsMapper.toCacheSummaryStatistics(classPathResourceCache),
            "DB/context records", cacheStatsMapper.toCacheSummaryStatistics(contextRecordCache));
    }

    public Optional<ContextRecord> cacheContextRecord(String key, Function<String, ContextRecord> loader) {
        return Optional.ofNullable(contextRecordCache.get(key, loader));
    }
}
