package fi.digitraffic.tis.vaco.summary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
import fi.digitraffic.tis.vaco.db.repositories.TaskRepository;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.summary.model.Summary;
import fi.digitraffic.tis.vaco.ui.model.summary.Card;
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

public class NetexInputSummaryServiceTests extends SpringBootIntegrationTestBase {

    private Path inputPath;
    private Task task;
    @Autowired
    private NetexInputSummaryService netexInputSummaryService;
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
        ImmutableEntry entryToCreate = TestObjects.anEntry("netex").build();
        EntryRecord entry = entryRepository.create(Optional.empty(), entryToCreate).get();
        taskRepository.createTasks(entry, List.of(ImmutableTask.of("FAKE_TASK", 1)));
        task = taskRepository.findTask(entry.id(), "FAKE_TASK").get();
        inputPath = Path.of(ClassLoader.getSystemResource("summary/211_netex.zip").toURI());
    }

    @Test
    void testNetexSummariesGeneration() {
        assertDoesNotThrow(() -> netexInputSummaryService.generateNetexInputSummaries(inputPath, task.id()));
        List<Summary> summaries = summaryRepository.findTaskSummaryByTaskId(task.id());

        summaries.forEach(summary -> {
            switch (summary.name()) {
                case "operators" -> {
                    List<Card> operators;
                    try {
                        operators = objectMapper.readValue(summary.raw(), new TypeReference<>() {
                        });
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    assertNotNull(operators);
                    assertEquals(3, operators.size());
                    assertEquals("Oulaisten Liikenne Oy", operators.get(0).title());
                    assertEquals("Vekka Group Oy", operators.get(1).title());
                    assertEquals("Kainuun Tilausliikenne P. Jääskeläinen Ky", operators.get(2).title());
                }
                case "lines" -> {
                    List<Card> lines;
                    try {
                        lines = objectMapper.readValue(summary.raw(), new TypeReference<>() {
                        });
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    assertNotNull(lines);
                    assertEquals(24, lines.size());
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("Linja 1")));
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("Linja 1L")));
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("Linja 3")));
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("Linja 4")));
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("Linja 6")));
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("Linja 7")));
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("pali")));
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("Linja 11")));
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("Linja 12")));
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("Linja 13")));
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("Linja 14")));
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("Linja 15")));
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("Linja 16")));
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("16K")));
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("Paakki - Sotkamo")));
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("Sotkamo - Paakki")));
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("Eevala - Juurikkalahti - Sotkamo")));
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("Sotkamo - Juurikkalahti - Eevala")));
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("Sipinen - Sotkamo - Tenetti")));
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("Tenetti - Sotkamo - Sipinen")));
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("Ontojoki - Sotkamo - Tenetti")));
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("Tenetti - Sotkamo - Ontojoki")));
                    assertTrue(lines.stream().anyMatch(line -> line.title().equals("Paikallisbussi SotKatti")));
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
                    assertEquals(26, files.size());
                    assertTrue(files.stream().anyMatch(file -> file.equals("211_Line-1_1_Linja 1.xml")));
                    assertTrue(files.stream().anyMatch(file -> file.equals("211_Line-SotKatti_411_Paikallisbussi SotKatti.xml")));
                    assertTrue(files.stream().anyMatch(file -> file.equals("211_shared_data.xml")));
                    assertTrue(files.stream().anyMatch(file -> file.equals("211_stops.xml")));
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
                    assertEquals(7, counts.size());
                    assertTrue(counts.stream().anyMatch(count -> count.equals("Journey patterns: 109")));
                    assertTrue(counts.stream().anyMatch(count -> count.equals("Lines: 24")));
                    assertTrue(counts.stream().anyMatch(count -> count.equals("Operators: 3")));
                    assertTrue(counts.stream().anyMatch(count -> count.equals("Quays: 937")));
                    assertTrue(counts.stream().anyMatch(count -> count.equals("Routes: 109")));
                    assertTrue(counts.stream().anyMatch(count -> count.equals("Service journeys: 259")));
                    assertTrue(counts.stream().anyMatch(count -> count.equals("Stop places: 934")));
                }
            }
        });
    }
}
