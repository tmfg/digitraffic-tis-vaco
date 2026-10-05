package fi.digitraffic.tis.vaco.rules.internal;

import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.http.VacoHttpClient;
import fi.digitraffic.tis.vaco.http.model.DownloadResponse;
import fi.digitraffic.tis.vaco.rules.RuleExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipInputStream;

/**
 * Maintains a local, S3-backed cache of the Stops and Quays NeTEx export published by Kooste, so
 * {@link StopsAndQuaysRule} always has a recent copy available without bundling a static file.
 */
@Service
public class StopsAndQuaysCacheService {

    private static final String CACHE_FILE_NAME = "stopsAndQuays-cache.zip";
    private static final S3Path CACHE_S3_KEY = ImmutableS3Path.of(List.of("static", "stopsAndQuaysCache.zip"));
    private static final int STARTUP_FETCH_ATTEMPTS = 3;
    private static final Duration STARTUP_RETRY_DELAY = Duration.ofSeconds(3);
    private static final int MAX_ZIP_ENTRIES = 10_000;
    private static final Duration STALE_AFTER = Duration.ofHours(30);

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final VacoProperties vacoProperties;
    private final VacoHttpClient vacoHttpClient;
    private final S3Client s3Client;
    private final Clock clock;

    private final AtomicReference<Path> cachedFile = new AtomicReference<>();
    private final AtomicReference<Instant> lastSuccessfulRefresh = new AtomicReference<>();
    private final ReentrantLock refreshLock = new ReentrantLock();

    public StopsAndQuaysCacheService(VacoProperties vacoProperties,
                                     VacoHttpClient vacoHttpClient,
                                     S3Client s3Client,
                                     Clock clock) {
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.vacoHttpClient = Objects.requireNonNull(vacoHttpClient);
        this.s3Client = Objects.requireNonNull(s3Client);
        this.clock = Objects.requireNonNull(clock);
    }

    public Optional<Path> currentCacheFile() {
        return Optional.ofNullable(cachedFile.get());
    }

    public Optional<Instant> lastSuccessfulRefreshAt() {
        return Optional.ofNullable(lastSuccessfulRefresh.get());
    }

    boolean isStale() {
        Instant last = lastSuccessfulRefresh.get();
        return last != null && Duration.between(last, clock.instant()).compareTo(STALE_AFTER) > 0;
    }

    @Scheduled(cron = "${vaco.scheduling.refresh-stops-and-quays.cron}", zone = "Europe/Helsinki")
    public void scheduledRefresh() {
        try {
            refresh();
        } catch (Exception e) {
            logger.warn("Failed to refresh stops and quays cache", e);
        }
        if (isStale()) {
            logger.error("Stops and quays cache has not been successfully refreshed in over {} hours", STALE_AFTER.toHours());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpOnStartup() {
        String bucket = vacoProperties.s3PackagesBucket();
        try {
            if (s3Client.keyExists(bucket, CACHE_S3_KEY.toString()) && restoreFromS3(bucket)) {
                return;
            }
        } catch (Exception e) {
            logger.warn("Failed to restore stops and quays cache from S3, falling back to eager fetch", e);
        }
        eagerFetchWithRetries();
    }

    private boolean restoreFromS3(String bucket) throws IOException {
        Path tempFile = tempDownloadPath();
        try {
            s3Client.downloadFile(bucket, CACHE_S3_KEY, tempFile);
            if (!isValidZip(tempFile)) {
                logger.warn("Cached stops and quays copy restored from S3 failed validation, falling back to eager fetch");
                return false;
            }
            Path stableFile = stableCacheFile();
            Files.move(tempFile, stableFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            cachedFile.set(stableFile);
            lastSuccessfulRefresh.set(clock.instant());
            return true;
        } finally {
            deleteIfExistsQuietly(tempFile);
        }
    }

    private void eagerFetchWithRetries() {
        for (int attempt = 1; attempt <= STARTUP_FETCH_ATTEMPTS; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                logger.warn("Thread interrupted, aborting eager fetch of stops and quays cache");
                return;
            }
            try {
                refresh();
            } catch (Exception e) {
                logger.warn("Eager fetch attempt {} of stops and quays cache failed", attempt, e);
            }
            if (cachedFile.get() != null) {
                return;
            }
            if (attempt < STARTUP_FETCH_ATTEMPTS) {
                sleepBeforeRetry();
            }
        }
        logger.warn("Failed to warm up stops and quays cache after {} attempts; cache remains empty until next scheduled refresh", STARTUP_FETCH_ATTEMPTS);
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(STARTUP_RETRY_DELAY.toMillis());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    private void refresh() {
        if (!refreshLock.tryLock()) {
            logger.info("Stops and quays cache refresh already in progress, skipping concurrent attempt");
            return;
        }
        try {
            Path tempFile = tempDownloadPath();
            try {
                DownloadResponse response = vacoHttpClient.downloadFile(tempFile, vacoProperties.stopsAndQuays().sourceUrl()).join();
                if (response == null || response.result() != DownloadResponse.Result.OK || response.body().isEmpty()) {
                    logger.warn("Failed to download stops and quays data, result was {}", response == null ? null : response.result());
                    return;
                }
                if (!isValidZip(tempFile)) {
                    logger.warn("Downloaded stops and quays data at {} is not a valid zip archive, discarding", tempFile);
                    return;
                }
                Path stableFile = stableCacheFile();
                Files.move(tempFile, stableFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                cachedFile.set(stableFile);
                lastSuccessfulRefresh.set(clock.instant());
                s3Client.uploadFile(vacoProperties.s3PackagesBucket(), CACHE_S3_KEY, stableFile).join();
            } catch (IOException e) {
                logger.warn("Failed to refresh stops and quays cache", e);
            } finally {
                deleteIfExistsQuietly(tempFile);
            }
        } finally {
            refreshLock.unlock();
        }
    }

    private boolean isValidZip(Path file) {
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(file))) {
            int entryCount = 0;
            while (zipInputStream.getNextEntry() != null) {
                entryCount++;
                if (entryCount > MAX_ZIP_ENTRIES) {
                    logger.warn("Zip archive at {} exceeds maximum allowed entry count of {}, rejecting", file, MAX_ZIP_ENTRIES);
                    return false;
                }
                zipInputStream.closeEntry();
            }
            return entryCount > 0;
        } catch (IOException _) {
            return false;
        }
    }

    private void deleteIfExistsQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.warn("Failed to delete temporary file {}", file, e);
        }
    }

    private Path cacheDirectory() {
        Path dir = Paths.get(vacoProperties.temporaryDirectory()).resolve("stopsAndQuays");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuleExecutionException("Failed to create stops and quays cache directory " + dir, e);
        }
        return dir;
    }

    private Path stableCacheFile() {
        return cacheDirectory().resolve(CACHE_FILE_NAME);
    }

    private Path tempDownloadPath() {
        return cacheDirectory().resolve(CACHE_FILE_NAME + "-" + UUID.randomUUID() + ".tmp");
    }
}
