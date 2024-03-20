package fi.digitraffic.tis.vaco.caching;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fi.digitraffic.tis.vaco.admintasks.model.GroupIdMappingTask;
import fi.digitraffic.tis.vaco.caching.mapper.CacheStatsMapper;
import fi.digitraffic.tis.vaco.caching.model.CacheSummaryStatistics;
import fi.digitraffic.tis.vaco.company.model.Hierarchy;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.PersistentEntry;
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

    public CachingService(CacheStatsMapper cacheStatsMapper) {
        this.cacheStatsMapper = Objects.requireNonNull(cacheStatsMapper);
        this.adminTasksCache = adminTasksCache();
        this.rulesetCache = rulesetNameCache();
        this.sqsQueueUrlCache = sqsQueueUrlCache();
        this.localPathCache = localPathCache();
        this.entryCache = entryCache();
        this.statusCache = statusCache();
        this.classPathResourceCache = classPathResourceCache();
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

    public void invalidateEntry(PersistentEntry entry) {
        invalidateEntry(entry.publicId());
    }

    public void invalidateEntry(Entry entry) {
        invalidateEntry(entry.publicId());
    }

    protected void invalidateEntry(String publicId) {
        entryCache.invalidate(publicId);
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

    public Optional<ClassPathResource> cacheClassPathResource(String key, Function<String, ClassPathResource> loader) {
        return Optional.ofNullable(classPathResourceCache.get(key, loader));
    }

    private Cache<String, GroupIdMappingTask> adminTasksCache() {
        return Caffeine.newBuilder()
            .recordStats()
            .maximumSize(100)
            .build();
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

    private Cache<String, Hierarchy> companyHierarchyCache() {
        return Caffeine.newBuilder()
            .recordStats()
            .maximumSize(200)
            .expireAfterWrite(Duration.ofHours(1))
            .build();
    }

    private Cache<String, Entry> entryCache() {
        return Caffeine.newBuilder()
            .recordStats()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofDays(1))
            .build();
    }

    private Cache<String, Status> statusCache() {
        return Caffeine.newBuilder()
            .recordStats()
            .maximumSize(3000)
            .expireAfterWrite(Duration.ofDays(1))
            .build();
    }

    private Cache<String, ClassPathResource> classPathResourceCache() {
        return Caffeine.newBuilder()
            .recordStats()
            .maximumSize(Status.values().length)
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
            "classpath resources", cacheStatsMapper.toCacheSummaryStatistics(classPathResourceCache));
    }


}
