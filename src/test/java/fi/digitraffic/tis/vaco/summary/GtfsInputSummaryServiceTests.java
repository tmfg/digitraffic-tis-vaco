package fi.digitraffic.tis.vaco.summary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.entries.EntryRepository;
import fi.digitraffic.tis.vaco.process.TaskRepository;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.PersistentEntry;
import fi.digitraffic.tis.vaco.summary.model.Summary;
import fi.digitraffic.tis.vaco.summary.model.gtfs.Agency;
import fi.digitraffic.tis.vaco.summary.model.gtfs.FeedInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @BeforeEach
    void setUp() throws URISyntaxException {
        ImmutableEntry entryToCreate = TestObjects.anEntry("gtfs").build();
        PersistentEntry entry = entryRepository.create(Optional.empty(), entryToCreate).get();
        taskRepository.createTasks(List.of(ImmutableTask.of(entry.id(), "FAKE_TASK", 1)));
        task = taskRepository.findTask(entry.id(), "FAKE_TASK").get();
        inputPath = Path.of(ClassLoader.getSystemResource("summary/211_gtfs.zip").toURI());
    }

    @Test
    public void testGtfsSummariesGeneration() {
        assertDoesNotThrow(() -> gtfsInputSummaryService.generateGtfsInputSummaries(inputPath, task.id()));
        List<Summary> summaries = summaryRepository.findTaskSummaryByTaskId(task.id());
        summaries.forEach(summary -> {
            switch (summary.name()) {
                case "agencies" -> {
                    List<Agency> agencies;
                    try {
                        agencies = objectMapper.readValue(summary.raw(), new TypeReference<>() {
                        });
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    assertNotNull(agencies);
                    assertEquals(6, agencies.size());
                    assertEquals("Oulaisten Liikenne Oy", agencies.get(0).getAgencyName());
                    assertEquals("info@oulaistenliikenne.fi", agencies.get(0).getAgencyEmail());
                }
                case "feedInfo" -> {
                    FeedInfo feedInfo;
                    try {
                        feedInfo = objectMapper.readValue(summary.raw(), new TypeReference<>() {
                        });
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    assertNotNull(feedInfo);
                    assertEquals("Kajaani", feedInfo.getFeedPublisherName());
                    assertEquals("http://kajaani.fi", feedInfo.getFeedPublisherUrl());
                    assertEquals("fi", feedInfo.getFeedLang());
                    assertEquals("20180808", feedInfo.getFeedStartDate());
                    assertEquals("20261231", feedInfo.getFeedEndDate());
                }
                case "files" -> {
                    List<String> files;
                    try {
                        files = objectMapper.readValue(summary.raw(), new TypeReference<>() {
                        });
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    assertNotNull(files);
                    assertEquals(13, files.size());
                    assertTrue(files.stream().anyMatch(file -> file.equals("agency.txt")));
                    assertTrue(files.stream().anyMatch(file -> file.equals("calendar_dates.txt")));
                    assertTrue(files.stream().anyMatch(file -> file.equals("calendar.txt")));
                    assertTrue(files.stream().anyMatch(file -> file.equals("contracts.txt")));
                    assertTrue(files.stream().anyMatch(file -> file.equals("feed_info.txt")));
                    assertTrue(files.stream().anyMatch(file -> file.equals("routes.txt")));
                    assertTrue(files.stream().anyMatch(file -> file.equals("shapes.txt")));
                    assertTrue(files.stream().anyMatch(file -> file.equals("stop_times.txt")));
                    assertTrue(files.stream().anyMatch(file -> file.equals("stops.txt")));
                    assertTrue(files.stream().anyMatch(file -> file.equals("transfers.txt")));
                    assertTrue(files.stream().anyMatch(file -> file.equals("translations.txt")));
                    assertTrue(files.stream().anyMatch(file -> file.equals("trip_notes.txt")));
                    assertTrue(files.stream().anyMatch(file -> file.equals("trips.txt")));
                }
                case "counts" -> {
                    List<String> counts;
                    try {
                        counts = objectMapper.readValue(summary.raw(), new TypeReference<>() {
                        });
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    assertNotNull(counts);
                    assertEquals(6, counts.size());
                    assertTrue(counts.stream().anyMatch(count -> count.equals("Stops: 938")));
                    assertTrue(counts.stream().anyMatch(count -> count.equals("Agencies: 6")));
                    assertTrue(counts.stream().anyMatch(count -> count.equals("Routes: 24")));
                    assertTrue(counts.stream().anyMatch(count -> count.equals("Trips: 259")));
                    assertTrue(counts.stream().anyMatch(count -> count.equals("Blocks: 3")));
                    assertTrue(counts.stream().anyMatch(count -> count.equals("Shapes: 97")));
                }
                case "components" -> {
                    List<String> components;
                    try {
                        components = objectMapper.readValue(summary.raw(), new TypeReference<>() {
                        });
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    assertNotNull(components);
                    assertEquals(10, components.size());
                    assertTrue(components.stream().anyMatch(component -> component.equals("Bikes Allowance")));
                    assertTrue(components.stream().anyMatch(component -> component.equals("Blocks")));
                    assertTrue(components.stream().anyMatch(component -> component.equals("Feed information")));
                    assertTrue(components.stream().anyMatch(component -> component.equals("Headsigns")));
                    assertTrue(components.stream().anyMatch(component -> component.equals("Location Types")));
                    assertTrue(components.stream().anyMatch(component -> component.equals("Route Colors")));
                    assertTrue(components.stream().anyMatch(component -> component.equals("Route Names")));
                    assertTrue(components.stream().anyMatch(component -> component.equals("Shapes")));
                    assertTrue(components.stream().anyMatch(component -> component.equals("Transfers")));
                    assertTrue(components.stream().anyMatch(component -> component.equals("Wheelchair Accessibility")));
                }
            }
        });
    }
}
