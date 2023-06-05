package fi.digitraffic.tis.vaco.validation.rules.gtfs;

import org.mobilitydata.gtfsvalidator.util.VersionInfo;
import org.mobilitydata.gtfsvalidator.util.VersionResolver;

import java.time.Duration;
import java.util.Optional;

/**
 * Canonical GTFS validator contains a home calling version check component which is completely pointless for us. This
 * variant disables the feature by enforcing the current version to be the latest.
 */
class StaticVersionResolver extends VersionResolver {
    @Override
    public VersionInfo getVersionInfoWithTimeout(Duration timeout) {
        return new VersionInfo() {
            @Override
            public Optional<String> currentVersion() {
                return Optional.of("4.0.0");
            }

            @Override
            public Optional<String> latestReleaseVersion() {
                return currentVersion();
            }

            @Override
            public boolean updateAvailable() {
                return false;
            }
        };
    }

    @Override
    public synchronized void resolve() {
        // do nothing, on purpose
    }
}
