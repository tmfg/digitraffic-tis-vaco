package fi.digitraffic.tis.vaco.cleanup;

import fi.digitraffic.tis.vaco.configuration.Cleanup;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
        List<String> removed = cleanupRepository.runCleanup(cleanupProperties.olderThan(), cleanupProperties.keepAtLeast());
        logger.info("Cleanup removed {} entries {}", removed.size(), removed);
        return removed;
    }

}
