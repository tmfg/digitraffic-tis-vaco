package fi.digitraffic.tis.vaco.cleanup;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestConstants;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;

class CleanupServiceIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    private EntryService entryService;

    @Autowired
    private CleanupService cleanupService;

    @Autowired
    private JdbcTemplate jdbc;

    private int totalEntries = 25;
    private Map<String, String> entries;

    @BeforeEach
    void setUp() {
        entries = IntStream.range(0, totalEntries)
            .mapToObj(i -> ImmutableEntry.of(Integer.toString(i), "whatever", "https://example.no/", TestConstants.FINTRAFFIC_BUSINESS_ID))
            .map(e -> {
                Entry result = entryService.create(e);
                // a bit of a hack: distribute entries over time so that the time part of duration query works in more
                // easily testable way
                jdbc.update("UPDATE entry SET updated=NOW() - INTERVAL '" + result.name() + " days' WHERE public_id = ?",
                    result.publicId());
                return entryService.findEntry(result.publicId(), true).get();
            })
            .collect(Collectors.toMap(Entry::publicId, Entry::name));
    }

    @AfterEach
    void tearDown() {
        entryService.findAllByBusinessId(TestConstants.FINTRAFFIC_BUSINESS_ID, true)
            .forEach(e -> jdbc.update("DELETE FROM entry WHERE public_id = ?", e.publicId()));
    }

    @Test
    void keepsGivenAmountOfEntries() {
        // remove anything without timelimit, keep 10 latest
        int keepEntries = 10;
        Duration olderThan = Duration.parse("P5D");
        List<String> removed = cleanupService.runCleanup(olderThan, keepEntries);

        // old are removed - the smaller the sequence number in name field, the older the entry is
        removed.forEach(r -> assertThat(Integer.parseInt(entries.get(r)), lessThan(totalEntries - keepEntries)));

        assertThat(removed.size(), equalTo(15));

        List<Entry> remaining = entryService.findAllByBusinessId(TestConstants.FINTRAFFIC_BUSINESS_ID, true);

        assertThat(remaining.size(), equalTo(10));

        // new are kept - the bigger the sequence number in name field, the newer the entry is
        remaining.forEach(r -> assertThat(Integer.parseInt(r.name()), greaterThanOrEqualTo(totalEntries - keepEntries)));
    }

    @Test
    void onlyRemovesEntriesOlderThanSpecified() {
        // keep everything newer than specified
        int keepEntries = 5;
        Duration olderThan = Duration.parse("P10D");
        List<String> removed = cleanupService.runCleanup(olderThan, keepEntries);

        // old are removed - the smaller the sequence number in name field, the older the entry is
        removed.forEach(r -> assertThat(Integer.parseInt(entries.get(r)), lessThan(20)));

        assertThat(removed.size(), equalTo(20));

        List<Entry> remaining = entryService.findAllByBusinessId(TestConstants.FINTRAFFIC_BUSINESS_ID, true);

        assertThat(remaining.size(), equalTo(5));

        // new are kept - the bigger the sequence number in name field, the newer the entry is
        remaining.forEach(r -> assertThat(Integer.parseInt(r.name()), greaterThanOrEqualTo(5)));
    }
}