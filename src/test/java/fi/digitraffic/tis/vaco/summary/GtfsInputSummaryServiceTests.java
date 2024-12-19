package fi.digitraffic.tis.vaco.summary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.db.model.SummaryRecord;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
import fi.digitraffic.tis.vaco.db.repositories.SummaryRepository;
import fi.digitraffic.tis.vaco.db.repositories.TaskRepository;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GtfsInputSummaryServiceTests extends SpringBootIntegrationTestBase {

    private Path inputPath;
    private Task task;
    @Autowired
    private GtfsInputSummaryService gtfsInputSummaryService;
    @Autowired
    EntryRepository entryRepository;
    @Autowired
    SummaryRepository summaryRepository;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordMapper recordMapper;

    private Entry entry;

    @BeforeEach
    void setUp() throws URISyntaxException {
        ImmutableEntry entryToCreate = TestObjects.anEntry("gtfs").build();
        EntryRecord entryRecord = entryRepository.create(entryToCreate, Optional.empty(), Optional.empty()).get();
        taskRepository.createTasks(entryRecord, List.of(ImmutableTask.of("FAKE_TASK", 1)));
        task = taskRepository.findTask(entryRecord.id(), "FAKE_TASK").get();
        inputPath = Path.of(ClassLoader.getSystemResource("summary/211_gtfs.zip").toURI());
        entry = recordMapper.toEntryBuilder(entryRecord, Optional.empty(), Optional.empty()).build();
    }

    @Test
    void testGtfsSummariesGeneration() throws Exception {
        assertDoesNotThrow(() -> gtfsInputSummaryService.generateGtfsInputSummaries(entry, task, inputPath));
        List<SummaryRecord> summaries = summaryRepository.findSummaryByTaskId(task.id());

        assertThat(summaries.size(), equalTo(5));

        List<Map<String, String>> agencies = objectMapper.readValue(summaries.getFirst().raw(), new TypeReference<>() {});
        assertEquals(6, agencies.size());
        assertEquals("Oulaisten Liikenne Oy", agencies.get(0).get("agency_name"));
        assertEquals("info@oulaistenliikenne.fi", agencies.get(0).get("agency_email"));
        // TODO: better asserts for the above

        Map<String, String> feedInfos = objectMapper.readValue(summaries.get(1).raw(), new TypeReference<>() {});
        assertNotNull(feedInfos);
        assertEquals(6, feedInfos.size());
        assertThat(feedInfos.get("feed_publisher_name"), equalTo("Kajaani"));
        assertThat(feedInfos.get("feed_publisher_url"), equalTo("http://kajaani.fi"));
        assertThat(feedInfos.get("feed_lang"), equalTo("fi"));
        assertThat(feedInfos.get("feed_start_date"), equalTo("20180808"));
        assertThat(feedInfos.get("feed_end_date"), equalTo("20261231"));
        assertThat(feedInfos.get("feed_version"), equalTo("202401261139"));

        Set<String> files = objectMapper.readValue(summaries.get(2).raw(), new TypeReference<>() {});
        Set<String> expectedFiles = Set.of("agency.txt",
            "calendar_dates.txt",
            "calendar.txt",
            "contracts.txt",
            "feed_info.txt",
            "routes.txt",
            "shapes.txt",
            "stop_times.txt",
            "stops.txt",
            "transfers.txt",
            "translations.txt",
            "trip_notes.txt",
            "trips.txt");
        assertThat(files, equalTo(expectedFiles));

        Set<String> counts = objectMapper.readValue(summaries.get(3).raw(), new TypeReference<>() {});
        Set<String> expectedCounts = Set.of("Stops: 938",
        "Agencies: 6",
        "Routes: 24",
        "Trips: 259",
        "Blocks: 3",
        "Shapes: 97");
        assertThat(counts, equalTo(expectedCounts));

        Set<String> components = objectMapper.readValue(summaries.get(4).raw(), new TypeReference<>() {});
        Set<String> expectedComponents = Set.of("Bikes Allowance",
            "Blocks",
            "Feed information",
            "Headsigns",
            "Location Types",
            "Route Colors",
            "Route Names",
            "Shapes",
            "Transfers",
            "Wheelchair Accessibility");
        assertThat(components, equalTo(expectedComponents));
    }
}
