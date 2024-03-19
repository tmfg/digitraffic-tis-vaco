package fi.digitraffic.tis.vaco.cleanup;

import fi.digitraffic.tis.vaco.configuration.Cleanup;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Service
public class CleanupService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Cleanup cleanupProperties;

    private final CleanupRepository cleanupRepository;

    public CleanupService(VacoProperties vacoProperties,
                          CleanupRepository cleanupRepository) {
        this.cleanupProperties = vacoProperties.cleanup();
        this.cleanupRepository = Objects.requireNonNull(cleanupRepository);
    }

    @Scheduled(cron = "${vaco.scheduling.cleanup.cron}")
    public void scheduledCleanup() {
        runCleanup();
    }

    public List<String> runCleanup() {
        return runCleanup(cleanupProperties.olderThan(), cleanupProperties.keepAtLeast());
    }

    public List<String> runCleanup(Duration olderThan, Integer keepAtLeast) {
        List<String> removed = cleanupRepository.runCleanup(cleanupOlderThan(olderThan), cleanupKeepAtLeast(keepAtLeast));
        logger.info("Cleanup removed {} entries {}", removed.size(), removed);
        return removed;
    }

    private Duration cleanupOlderThan(Duration olderThanRequest) {
        if (olderThanRequest == null) {
            return cleanupProperties.olderThan();
        }
        if (olderThanRequest.compareTo(Cleanup.MINIMUM_CLEANUP_DURATION) < 0) {
            logger.warn("Tried to run cleanup with less than allowed minimum duration of {}! Using minimum value instead of {}", Cleanup.MINIMUM_CLEANUP_DURATION, olderThanRequest);
            return Cleanup.MINIMUM_CLEANUP_DURATION;
        }
        return olderThanRequest;
    }

    private int cleanupKeepAtLeast(Integer keepAtLeastRequest) {
        if (keepAtLeastRequest == null) {
            return cleanupProperties.keepAtLeast();
        }
        if (keepAtLeastRequest < Cleanup.MINIMUM_KEEP_AT_LEAST) {
            logger.warn("Tried to run cleanup with less than allowed minimum keep at least value of {}! Using minimum value instead of {}", Cleanup.MINIMUM_KEEP_AT_LEAST, keepAtLeastRequest);
            return Cleanup.MINIMUM_KEEP_AT_LEAST;
        }
        return keepAtLeastRequest;
    }
}
