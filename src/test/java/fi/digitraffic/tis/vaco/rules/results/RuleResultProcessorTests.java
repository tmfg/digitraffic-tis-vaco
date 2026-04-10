package fi.digitraffic.tis.vaco.rules.results;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.findings.FindingService;
import fi.digitraffic.tis.vaco.findings.model.ImmutableFinding;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.RulesetType;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static fi.digitraffic.tis.vaco.rules.ResultProcessorTestHelpers.entryWithTask;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class RuleResultProcessorTests {

    @Mock private PackagesService packagesService;
    @Mock private S3Client s3Client;
    @Mock private TaskService taskService;
    @Mock private FindingService findingService;
    @Mock private RulesetService rulesetService;

    private RuleResultProcessor processor;
    private Entry entry;
    private Task task;
    private ImmutableRuleset netex2gtfsRuleset;

    @BeforeEach
    void setUp() {
        VacoProperties vacoProperties = TestObjects.vacoProperties();
        processor = new RuleResultProcessor(vacoProperties, packagesService, s3Client, taskService, findingService, rulesetService, new ObjectMapper()) {
            @Override
            boolean doProcessResults(ResultMessage resultMessage, Entry entry, Task task, Map<String, String> fileNames) {
                return false;
            }
        };
        entry = entryWithTask(e -> ImmutableTask.of(RuleName.NETEX2GTFS_ENTUR, 100)
            .withId(9_000_000L)
            .withPublicId(NanoIdUtils.randomNanoId()));
        task = entry.tasks().get(0);
        netex2gtfsRuleset = ImmutableRuleset.of(
                RuleName.NETEX2GTFS_ENTUR,
                "netex2gtfs",
                Category.GENERIC,
                RulesetType.CONVERSION_SYNTAX,
                TransitDataFormat.NETEX)
            .withId(1L);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(rulesetService);
    }

    // --- isNonErrorLogLine tests ---

    @ParameterizedTest
    @ValueSource(strings = {
        "23:28.742 [main] INFO org.onebusaway.gtfs.serialization.GtfsWriter -- writing entities: org.onebusaway.gtfs.model.RouteNameException",
        "12:00.000 [pool-1] WARN org.foo.Bar -- Something about SomeException handling",
        "12:00.000 [pool-1] DEBUG org.foo.Bar -- Caught RouteNameException",
        "12:00.000 [pool-1] TRACE org.foo.Bar -- Exception class loaded",
        "12:00.000 [main] INFO org.foo.ExceptionMapper -- starting up"
    })
    void isNonErrorLogLine_returnsTrue_forNonErrorLevelLinesWithExceptionInContent(String line) {
        boolean result = RuleResultProcessor.isNonErrorLogLine(line);
        assertTrue(result, "Expected line to be excluded as non-error: " + line);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "12:00.000 [main] ERROR org.foo.Bar -- SomeException: bad stuff",
        "java.lang.RuntimeException: something broke",
        "Caused by: java.io.IOException: file not found",
        "Exception in thread \"main\" java.lang.OutOfMemoryError",
        "",
        "Writing entity RouteNameException"
    })
    void isNonErrorLogLine_returnsFalse_forErrorLinesAndStackTraces(String line) {
        boolean result = RuleResultProcessor.isNonErrorLogLine(line);
        assertFalse(result, "Expected line NOT to be excluded: " + line);
    }

    // --- scanErrorLog tests ---

    @Test
    void scanErrorLog_returnsNoFindings_whenLogIsEmpty(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("empty.log");
        Files.writeString(logFile, "");
        given(rulesetService.findByName(RuleName.NETEX2GTFS_ENTUR)).willReturn(Optional.of(netex2gtfsRuleset));

        List<ImmutableFinding> findings = processor.scanErrorLog(entry, task, RuleName.NETEX2GTFS_ENTUR, logFile, "Exception");

        assertThat(findings, empty());
    }

    @Test
    void scanErrorLog_returnsNoFindings_whenAllExceptionLinesAreInfoLevel(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("info-only.log");
        Files.writeString(logFile,
            "23:28.742 [main] INFO org.onebusaway.gtfs.serialization.GtfsWriter -- writing entities: org.onebusaway.gtfs.model.RouteNameException\n" +
            "23:28.742 [main] INFO org.onebusaway.gtfs.serialization.GtfsWriter -- writing entities: org.onebusaway.gtfs.model.DirectionNameException\n" +
            "23:28.742 [main] INFO org.onebusaway.gtfs.serialization.GtfsWriter -- writing entities: org.onebusaway.gtfs.model.AlternateStopNameException\n");
        given(rulesetService.findByName(RuleName.NETEX2GTFS_ENTUR)).willReturn(Optional.of(netex2gtfsRuleset));

        List<ImmutableFinding> findings = processor.scanErrorLog(entry, task, RuleName.NETEX2GTFS_ENTUR, logFile, "Exception");

        assertThat(findings, empty());
    }

    @Test
    void scanErrorLog_returnsFinding_whenExceptionLineMixedWithInfoLines(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("mixed.log");
        Files.writeString(logFile,
            "23:28.742 [main] INFO org.onebusaway.gtfs.serialization.GtfsWriter -- writing entities: org.onebusaway.gtfs.model.RouteNameException\n" +
            "12:00.000 [main] ERROR org.foo.Bar -- SomeException: bad stuff\n" +
            "23:28.742 [main] INFO org.onebusaway.gtfs.serialization.GtfsWriter -- writing entities: org.onebusaway.gtfs.model.DirectionNameException\n");
        given(rulesetService.findByName(RuleName.NETEX2GTFS_ENTUR)).willReturn(Optional.of(netex2gtfsRuleset));

        List<ImmutableFinding> findings = processor.scanErrorLog(entry, task, RuleName.NETEX2GTFS_ENTUR, logFile, "Exception");

        assertThat(findings, hasSize(1));
    }

    @Test
    void scanErrorLog_returnsFinding_whenErrorLevelExceptionLine(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("error.log");
        Files.writeString(logFile, "12:00.000 [main] ERROR org.foo.Bar -- BrokenException: failure\n");
        given(rulesetService.findByName(RuleName.NETEX2GTFS_ENTUR)).willReturn(Optional.of(netex2gtfsRuleset));

        List<ImmutableFinding> findings = processor.scanErrorLog(entry, task, RuleName.NETEX2GTFS_ENTUR, logFile, "Exception");

        assertThat(findings, hasSize(1));
    }

    @Test
    void scanErrorLog_returnsFinding_whenUnstructuredExceptionLine(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("unstructured.log");
        Files.writeString(logFile, "java.lang.NullPointerException: cannot be null\n");
        given(rulesetService.findByName(RuleName.NETEX2GTFS_ENTUR)).willReturn(Optional.of(netex2gtfsRuleset));

        List<ImmutableFinding> findings = processor.scanErrorLog(entry, task, RuleName.NETEX2GTFS_ENTUR, logFile, "Exception");

        assertThat(findings, hasSize(1));
    }

    @Test
    void scanErrorLog_returnsFinding_whenCausedByLine(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("causedby.log");
        Files.writeString(logFile, "Caused by: java.io.IOException: file not found\n");
        given(rulesetService.findByName(RuleName.NETEX2GTFS_ENTUR)).willReturn(Optional.of(netex2gtfsRuleset));

        List<ImmutableFinding> findings = processor.scanErrorLog(entry, task, RuleName.NETEX2GTFS_ENTUR, logFile, "Exception");

        assertThat(findings, hasSize(1));
    }

    @Test
    void scanErrorLog_returnsFinding_whenNodeJsErrorLine(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("nodejs.log");
        Files.writeString(logFile, "TypeError: Cannot read property 'id' of undefined\n");
        given(rulesetService.findByName(RuleName.NETEX2GTFS_ENTUR)).willReturn(Optional.of(netex2gtfsRuleset));

        List<ImmutableFinding> findings = processor.scanErrorLog(entry, task, RuleName.NETEX2GTFS_ENTUR, logFile, "Error");

        assertThat(findings, hasSize(1));
    }
}
