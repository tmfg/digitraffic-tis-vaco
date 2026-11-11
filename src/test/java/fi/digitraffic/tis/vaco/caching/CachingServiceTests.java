package fi.digitraffic.tis.vaco.caching;

import fi.digitraffic.tis.vaco.caching.mapper.CacheStatsMapper;
import fi.digitraffic.tis.vaco.db.model.ContextRecord;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.mock;

class CachingServiceTests {

    private CachingService cachingService;

    private List<String> loaded;

    @BeforeEach
    void setUp() {
        cachingService = new CachingService(new CacheStatsMapper());
        loaded = new ArrayList<>();
    }

    @Test
    void invalidateContextRecordsRemovesOnlyTheCompanysContexts() {
        cacheContext("1234567-8/a");
        cacheContext("1234567-8/b");
        cacheContext("1234567-80/a");
        loaded.clear();

        cachingService.invalidateContextRecords("1234567-8");
        cacheContext("1234567-8/a");
        cacheContext("1234567-8/b");
        cacheContext("1234567-80/a");

        assertThat(loaded, contains("1234567-8/a", "1234567-8/b"));
    }

    @Test
    void invalidateEntryAndTaskStatusesRemovesTheEntryAndAllItsStatuses() {
        cacheEntry("abc");
        cacheStatus("abc");
        cacheStatus("abc/prepare.download");
        cacheStatus("abcd");
        loaded.clear();

        cachingService.invalidateEntryAndTaskStatuses("abc");
        cacheEntry("abc");
        cacheStatus("abc");
        cacheStatus("abc/prepare.download");
        cacheStatus("abcd");

        assertThat(loaded, contains("entry abc", "status abc", "status abc/prepare.download"));
    }

    private void cacheContext(String key) {
        cachingService.cacheContextRecord(key, k -> {
            loaded.add(k);
            return mock(ContextRecord.class);
        });
    }

    private void cacheEntry(String key) {
        cachingService.cacheEntry(key, k -> {
            loaded.add("entry " + k);
            return mock(Entry.class);
        });
    }

    private void cacheStatus(String key) {
        cachingService.cacheStatus(key, k -> {
            loaded.add("status " + k);
            return Status.SUCCESS;
        });
    }
}
