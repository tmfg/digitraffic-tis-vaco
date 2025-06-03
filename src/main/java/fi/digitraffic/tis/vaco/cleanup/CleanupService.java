package fi.digitraffic.tis.vaco.cleanup;

import com.google.common.annotations.VisibleForTesting;
import fi.digitraffic.tis.vaco.configuration.Cleanup;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
import fi.digitraffic.tis.vaco.featureflags.FeatureFlagsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class CleanupService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Cleanup cleanupProperties;

    private final FeatureFlagsService featureFlagsService;

    private final EntryRepository entryRepository;

    public CleanupService(VacoProperties vacoProperties,
                          FeatureFlagsService featureFlagsService,
                          EntryRepository entryRepository) {
        this.cleanupProperties = vacoProperties.cleanup();
        this.featureFlagsService = Objects.requireNonNull(featureFlagsService);
        this.entryRepository = Objects.requireNonNull(entryRepository);
    }

    @Scheduled(cron = "${vaco.scheduling.cleanup.cron}")
    public void scheduledCleanup() {
        if (featureFlagsService.isFeatureFlagEnabled("scheduledTasks.oldDataCleanup")) {
            runCleanup();
        } else {
            logger.info("Feature flag 'scheduledTasks.oldDataCleanup' is currently disabled, will not run scheduled cleanup");
        }
    }

    public Set<String> runCleanup() {
        return runCleanup(cleanupProperties.historyOlderThan(), cleanupProperties.entriesWithoutContextOlderThan(), cleanupProperties.keepAtLeast(), cleanupProperties.removeAtMostInTotal());
    }

    public Set<String> runCleanup(Duration historyOlderThan, Duration entriesWithoutContextOlderThan, Integer keepAtLeast, Integer atMostInTotal) {
        // 1. remove in-between cancelled entries no one cares about
        List<String> removedByCompression = entryRepository.compressHistory();
        // 2. run generic cleanup
        List<String> removedByCleanup = entryRepository.cleanupHistory(
            cleanupHistoryOlderThan(historyOlderThan),
            cleanupKeepAtLeast(keepAtLeast),
            cleanupRemoveAtMostInTotal(atMostInTotal));
        // 3. remove entries without context id
        List<String> removedByWithoutContextCleanUp = entryRepository.cleanEntriesWithoutContext(
            cleanupEntriesWithoutContextOlderThan(entriesWithoutContextOlderThan));
        if (logger.isInfoEnabled()) {
            int count = removedByCompression.size() + removedByCleanup.size() + removedByWithoutContextCleanUp.size();
            logger.info("Cleanup removed {} entries (compressed: {}, cleaned up: {}, cleaned up without context: {})", count, removedByCompression, removedByCleanup, removedByWithoutContextCleanUp);
        }
        Set<String> allRemoved = new HashSet<>();
        allRemoved.addAll(removedByCompression);
        allRemoved.addAll(removedByCleanup);
        allRemoved.addAll(removedByWithoutContextCleanUp);
        return allRemoved;
    }

    @VisibleForTesting
    protected Duration cleanupHistoryOlderThan(Duration olderThanRequest) {
        return cleanupEntriesOlderThan(olderThanRequest, cleanupProperties.historyOlderThan());
    }

    @VisibleForTesting
    protected Duration cleanupEntriesWithoutContextOlderThan(Duration olderThanRequest) {
        return cleanupEntriesOlderThan(olderThanRequest, cleanupProperties.entriesWithoutContextOlderThan());
    }

    private Duration cleanupEntriesOlderThan(Duration olderThanRequest, Duration fallback) {
        if (olderThanRequest == null) {
            olderThanRequest = fallback;
        }
        if (olderThanRequest.compareTo(Cleanup.MINIMUM_CLEANUP_DURATION) < 0) {
            logger.warn("Tried to run cleanup with less than allowed minimum duration of {}! Using minimum value instead of {}", Cleanup.MINIMUM_CLEANUP_DURATION, olderThanRequest);
            return Cleanup.MINIMUM_CLEANUP_DURATION;
        }
        return olderThanRequest;
    }

    @VisibleForTesting
    protected int cleanupKeepAtLeast(Integer keepAtLeastRequest) {
        if (keepAtLeastRequest == null) {
            keepAtLeastRequest = cleanupProperties.keepAtLeast();
        }
        if (keepAtLeastRequest < Cleanup.MINIMUM_KEEP_AT_LEAST) {
            logger.warn("Tried to run cleanup with less than allowed minimum keep at least value of {}! Using minimum value instead of {}", Cleanup.MINIMUM_KEEP_AT_LEAST, keepAtLeastRequest);
            return Cleanup.MINIMUM_KEEP_AT_LEAST;
        }
        return keepAtLeastRequest;
    }

    @VisibleForTesting
    protected int cleanupRemoveAtMostInTotal(Integer atMostInTotal) {
        if (atMostInTotal == null) {
            atMostInTotal = cleanupProperties.removeAtMostInTotal();
        }
        if (atMostInTotal > Cleanup.MAXIMUM_REMOVE_AT_MOST_IN_TOTAL) {
            logger.warn("Tried to run cleanup with more than allowed maximum entries to remove in total of {}! Using maximum value instead of {}", Cleanup.MAXIMUM_REMOVE_AT_MOST_IN_TOTAL, atMostInTotal);
            return Cleanup.MAXIMUM_REMOVE_AT_MOST_IN_TOTAL;
        }
        return atMostInTotal;
    }
}
