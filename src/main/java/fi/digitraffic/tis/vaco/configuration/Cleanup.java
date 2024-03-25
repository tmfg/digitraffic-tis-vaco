package fi.digitraffic.tis.vaco.configuration;

import java.time.Duration;

public record Cleanup(Duration olderThan, int keepAtLeast, int removeAtMostInTotal) {
    public static final int MINIMUM_KEEP_AT_LEAST = 3;
    public static final Duration MINIMUM_CLEANUP_DURATION = Duration.ofDays(7);
    public static final int MAXIMUM_REMOVE_AT_MOST_IN_TOTAL = 1_000;
}
