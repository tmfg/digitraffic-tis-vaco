package fi.digitraffic.tis.vaco.configuration;

import java.time.Duration;

public record Cleanup(Duration olderThan, int keepAtLeast) {
}
