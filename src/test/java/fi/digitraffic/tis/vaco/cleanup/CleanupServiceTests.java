package fi.digitraffic.tis.vaco.cleanup;

import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.Cleanup;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
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
    private EntryRepository entryRepository;

    @Mock
    private FeatureFlagsService featureFlagsService;

    @BeforeEach
    void setUp() {
        vacoProperties = TestObjects.vacoProperties();
        cleanupService = new CleanupService(vacoProperties, featureFlagsService, entryRepository);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(entryRepository, featureFlagsService);
    }

    @Test
    void returnsDefaultCleanupValuesForNull() {
        assertThat(cleanupService.cleanupKeepAtLeast(null), equalTo(vacoProperties.cleanup().keepAtLeast()));
        assertThat(cleanupService.cleanupRemoveAtMostInTotal(null), equalTo(vacoProperties.cleanup().removeAtMostInTotal()));
    }

    @Test
    void returnsHardcodedLimitValuesForValuesOutOfBounds() {
        assertThat(cleanupService.cleanupHistoryOlderThan(Duration.ofSeconds(1)), equalTo(Cleanup.MINIMUM_CLEANUP_DURATION));
        assertThat(cleanupService.cleanupEntriesWithoutContextOlderThan(Duration.ofSeconds(2)), equalTo(Cleanup.MINIMUM_CLEANUP_DURATION));
        assertThat(cleanupService.cleanupKeepAtLeast(0), equalTo(Cleanup.MINIMUM_KEEP_AT_LEAST));
        assertThat(cleanupService.cleanupRemoveAtMostInTotal(1_000_000), equalTo(Cleanup.MAXIMUM_REMOVE_AT_MOST_IN_TOTAL));
    }

    @Test
    void returnsGivenValuesIfTheyAreWithinAllowedRanges() {
        // duration doesn't have upper bound
        assertThat(cleanupService.cleanupHistoryOlderThan(Duration.ofDays(600)), equalTo(Duration.ofDays(600)));
        assertThat(cleanupService.cleanupEntriesWithoutContextOlderThan(Duration.ofDays(700)), equalTo(Duration.ofDays(700)));
        assertThat(cleanupService.cleanupKeepAtLeast(100), equalTo(100));
        assertThat(cleanupService.cleanupRemoveAtMostInTotal(42), equalTo(42));
    }
}
