package fi.digitraffic.tis.vaco.cleanup;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestConstants;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private EntryRepository entryRepository;

    @Autowired
    private CleanupService cleanupService;

    @Autowired
    private JdbcTemplate jdbc;

    private final int totalEntries = 25;
    private Map<String, String> entries;

    @BeforeEach
    void setUp() {
        entries = IntStream.range(0, totalEntries)
            .mapToObj(i -> ImmutableEntry.of(NanoIdUtils.randomNanoId(), Integer.toString(i), "whatever", "https://example.no/", TestConstants.FINTRAFFIC_BUSINESS_ID, false))
            .map(this::interleaveEntry)
            .collect(Collectors.toMap(Entry::publicId, Entry::name));
    }

    /**
     * Bit of a hack: distribute entries over time so that the time part of duration query works in more easily testable
     * way
     */
    @NotNull
    private Entry interleaveEntry(Entry e) {
        EntryRecord result = entryRepository.create(e, Optional.empty(), Optional.empty()).orElseThrow();
        String days = String.format("%d", totalEntries - Integer.parseInt(result.name()));
        jdbc.update("UPDATE entry SET created=timezone('utc', now())- INTERVAL '" + days + " DAYS' WHERE public_id = ?",
            result.publicId());
        return entryService.findEntry(result.publicId()).get();
    }

    @AfterEach
    void tearDown() {
        entryService.findAllByBusinessId(TestConstants.FINTRAFFIC_BUSINESS_ID)
            .forEach(e -> jdbc.update("DELETE FROM entry WHERE public_id = ?", e.publicId()));
    }

    @Test
    void keepsGivenAmountOfHistoryEntries() {
        // keep 10 latest entries
        int keepEntries = 10;
        Duration historyOlderThan = Duration.parse("P20D");
        Duration entriesWithoutContextOlderThan = Duration.parse("P20D");
        int atMost = 100;
        Set<String> removed = cleanupService.runCleanup(historyOlderThan, entriesWithoutContextOlderThan, keepEntries, atMost);

        // old are removed - the smaller the sequence number in name field, the older the entry is
        removed.forEach(r -> assertThat(Integer.parseInt(entries.get(r)), lessThan(totalEntries - keepEntries)));

        assertThat(removed.size(), equalTo(15));

        List<Entry> remaining = entryService.findAllByBusinessId(TestConstants.FINTRAFFIC_BUSINESS_ID);

        assertThat(remaining.size(), equalTo(10));

        // new are kept - the bigger the sequence number in name field, the newer the entry is
        remaining.forEach(r -> assertThat(Integer.parseInt(r.name()), greaterThanOrEqualTo(totalEntries - keepEntries)));
    }

    @Test
    void deletesOldEntries() {
        // Delete entries older than 8 days
        int keepEntries = 100;
        Duration historyOlderThan = Duration.parse("P9D");
        // use larger value here to ensure that history -rule gets tested properly
        Duration entriesWithoutContextOlderThan = Duration.parse("P20D");
        int atMost = 100;
        Set<String> removed = cleanupService.runCleanup(historyOlderThan, entriesWithoutContextOlderThan, keepEntries, atMost);

        assertThat(removed.size(), equalTo(17));

        List<Entry> remaining = entryService.findAllByBusinessId(TestConstants.FINTRAFFIC_BUSINESS_ID);

        assertThat(remaining.size(), equalTo(8));

        // new are kept - the bigger the sequence number in name field, the newer the entry is
        remaining.forEach(r -> assertThat(Integer.parseInt(r.name()), greaterThanOrEqualTo(17)));
    }

    @Test
    void onlyRemovesEntriesOlderThanSpecified() {
        // keep 5 latest entries
        int keepEntries = 5;
        Duration historyOlderThan = Duration.parse("P10D");
        Duration entriesWithoutContextOlderThan = Duration.parse("P10D");
        int atMost = 100;
        Set<String> removed = cleanupService.runCleanup(historyOlderThan, entriesWithoutContextOlderThan, keepEntries, atMost);

        // old are removed - the smaller the sequence number in name field, the older the entry is
        removed.forEach(r -> assertThat(Integer.parseInt(entries.get(r)), lessThan(20)));

        assertThat(removed.size(), equalTo(20));

        List<Entry> remaining = entryService.findAllByBusinessId(TestConstants.FINTRAFFIC_BUSINESS_ID);

        assertThat(remaining.size(), equalTo(5));

        // new are kept - the bigger the sequence number in name field, the newer the entry is
        remaining.forEach(r -> assertThat(Integer.parseInt(r.name()), greaterThanOrEqualTo(5)));
    }

    @Test
    void removeOldEntriesWithoutContext() {
        // keep everything newer than specified
        int keepEntries = 100;
        // use large value here to ensure that the entries without context -rule gets tested properly
        Duration historyOlderThan = Duration.parse("P20D");
        Duration entriesWithoutContextOlderThan = Duration.parse("P9D");
        int atMost = 100;
        Set<String> removed = cleanupService.runCleanup(historyOlderThan, entriesWithoutContextOlderThan, keepEntries, atMost);

        assertThat(removed.size(), equalTo(17));

        List<Entry> remaining = entryService.findAllByBusinessId(TestConstants.FINTRAFFIC_BUSINESS_ID);

        assertThat(remaining.size(), equalTo(8));

        // new are kept - the bigger the sequence number in name field, the newer the entry is
        remaining.forEach(r -> assertThat(Integer.parseInt(r.name()), greaterThanOrEqualTo(17)));
    }

    @Test
    void removesSubsequentCancelledEntriesToReduceClutter() {
        int cancelledEntryCount = 5;
        List<EntryRecord> cancelledEntries = IntStream.range(0, cancelledEntryCount)
            .mapToObj(i -> ImmutableEntry.of(NanoIdUtils.randomNanoId(), Integer.toString(i), "whatever", "https://example.co.uk/cancelled", TestConstants.FINTRAFFIC_BUSINESS_ID, false))
            .map(e -> entryRepository.create(e, Optional.empty(), Optional.empty()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();

        assertThat(cancelledEntries.size(), equalTo(cancelledEntryCount));

        Set<String> removed = cleanupService.runCleanup();

        // first is removed
        assertThat(removed.contains(cancelledEntries.get(0).publicId()), equalTo(false));
        // last is removed
        assertThat(removed.contains(cancelledEntries.get(cancelledEntryCount - 1).publicId()), equalTo(false));
        // in-between entries are kept
        cancelledEntries.subList(1, cancelledEntryCount - 1).forEach(e -> {
            assertThat(removed.contains(e.publicId()), equalTo(false));
        });
    }
}
