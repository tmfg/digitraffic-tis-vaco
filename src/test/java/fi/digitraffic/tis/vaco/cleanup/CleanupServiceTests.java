package fi.digitraffic.tis.vaco.cleanup;

import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.Cleanup;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.db.repositories.CleanupRepository;
import fi.digitraffic.tis.vaco.featureflags.FeatureFlagsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class CleanupServiceTests {

    private CleanupService cleanupService;

    private VacoProperties vacoProperties;

    @Mock
    private CleanupRepository cleanupRepository;

    @Mock
    private FeatureFlagsService featureFlagsService;

    @BeforeEach
    void setUp() {
        vacoProperties = TestObjects.vacoProperties();
        cleanupService = new CleanupService(vacoProperties, cleanupRepository, featureFlagsService);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(cleanupRepository, featureFlagsService);
    }

    @Test
    void returnsDefaultCleanupValuesForNull() {
        assertThat(cleanupService.cleanupOlderThan(null), equalTo(vacoProperties.cleanup().olderThan()));
        assertThat(cleanupService.cleanupKeepAtLeast(null), equalTo(vacoProperties.cleanup().keepAtLeast()));
        assertThat(cleanupService.cleanupRemoveAtMostInTotal(null), equalTo(vacoProperties.cleanup().removeAtMostInTotal()));
    }

    @Test
    void returnsHardcodedLimitValuesForValuesOutOfBounds() {
        assertThat(cleanupService.cleanupOlderThan(Duration.ofSeconds(1)), equalTo(Cleanup.MINIMUM_CLEANUP_DURATION));
        assertThat(cleanupService.cleanupKeepAtLeast(0), equalTo(Cleanup.MINIMUM_KEEP_AT_LEAST));
        assertThat(cleanupService.cleanupRemoveAtMostInTotal(1_000_000), equalTo(Cleanup.MAXIMUM_REMOVE_AT_MOST_IN_TOTAL));
    }

    @Test
    void returnsGivenValuesIfTheyAreWithinAllowedRanges() {
        // duration doesn't have upper bound
        assertThat(cleanupService.cleanupOlderThan(Duration.ofDays(600)), equalTo(Duration.ofDays(600)));
        assertThat(cleanupService.cleanupKeepAtLeast(100), equalTo(100));
        assertThat(cleanupService.cleanupRemoveAtMostInTotal(42), equalTo(42));
    }
}
