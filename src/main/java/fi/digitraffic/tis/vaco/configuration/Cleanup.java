package fi.digitraffic.tis.vaco.configuration;

import java.time.Duration;

public record Cleanup(Duration olderThan, int keepAtLeast) {
    public static final int MINIMUM_KEEP_AT_LEAST = 3;
    public static final Duration MINIMUM_CLEANUP_DURATION = Duration.ofDays(7);
}
