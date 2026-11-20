package fi.digitraffic.tis.vaco.rules.results;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.findings.FindingService;
import fi.digitraffic.tis.vaco.findings.model.Finding;
import fi.digitraffic.tis.vaco.packages.model.ImmutablePackage;
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
import fi.digitraffic.tis.vaco.summary.SsimSummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static fi.digitraffic.tis.vaco.rules.ResultProcessorTestHelpers.asResultMessage;
import static fi.digitraffic.tis.vaco.rules.ResultProcessorTestHelpers.entryWithTask;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SsimFintrafficResultProcessorTests extends ResultProcessorTestBase {

    @Mock private S3Client s3Client;
    @Mock private FindingService findingService;
    @Mock private RulesetService rulesetService;
    @Mock private SsimSummaryService ssimSummaryService;
    @Captor private ArgumentCaptor<List<Finding>> generatedFindings;

    private SsimFintrafficResultProcessor resultProcessor;
    private VacoProperties vacoProperties;
    private Entry entry;
    private Task task;
    private ResultMessage resultMessage;
    private ImmutableRuleset ssimRuleset;
    private final Map<String, Path> tempFiles = new HashMap<>();

    @BeforeEach
    void setUp() {
        vacoProperties = TestObjects.vacoProperties();
        ObjectMapper objectMapper = JsonMapper.builder().build();
        resultProcessor = new SsimFintrafficResultProcessor(
            vacoProperties,
            packagesService,
            s3Client,
            taskService,
            findingService,
            rulesetService,
            objectMapper,
            ssimSummaryService) {
            @Override
            protected Path downloadFile(Map<String, String> fileNames, String fileName, Path outputDir) {
                return tempFiles.get(fileName);
            }
        };
        entry = entryWithTask(e -> ImmutableTask.of(RuleName.SSIM_FINTRAFFIC, 100)
            .withId(9_000_000L)
            .withPublicId(NanoIdUtils.randomNanoId()));
        task = entry.tasks().getFirst();
        resultMessage = asResultMessage(vacoProperties, RuleName.SSIM_FINTRAFFIC, entry, Map.of(
            "report.json", List.of("report"),
            "summary.json", List.of("summary"),
            "stdout.log", List.of("debug"),
            "stderr.log", List.of("debug")
        ));
        ssimRuleset = ImmutableRuleset.of(
                RuleName.SSIM_FINTRAFFIC,
                "SSIM validation",
                Category.GENERIC,
                RulesetType.VALIDATION_SYNTAX,
                TransitDataFormat.SSIM)
            .withId(42L);
    }

    @Test
    void mapsReportFindingsAndGeneratesSummary(@TempDir Path tempDir) throws IOException {
        tempFiles.put("report.json", write(tempDir, "report.json",
            """
                {"findings":[{"code":"invalid_flight","severity":"ERROR","context":{"flight":"AB123"}}]}
                """));
        tempFiles.put("summary.json", write(tempDir, "summary.json", "{}"));
        tempFiles.put("stdout.log", write(tempDir, "stdout.log", ""));
        tempFiles.put("stderr.log", write(tempDir, "stderr.log", ""));

        givenPackageIsCreated("report", entry, task).willReturn(ImmutablePackage.of(task, "report", IGNORED_PATH_VALUE));
        givenPackageIsCreated("summary", entry, task).willReturn(ImmutablePackage.of(task, "summary", IGNORED_PATH_VALUE));
        givenPackageIsCreated("debug", entry, task).willReturn(ImmutablePackage.of(task, "debug", IGNORED_PATH_VALUE));
        given(rulesetService.findByName(RuleName.SSIM_FINTRAFFIC)).willReturn(Optional.of(ssimRuleset));
        given(findingService.reportFindings(generatedFindings.capture())).willReturn(true);
        given(findingService.summarizeFindingsSeverities(task)).willReturn(Map.of());
        givenTaskStatusIsMarkedAs(entry, Status.SUCCESS);
        givenTaskProcessingStateIsMarkedAs(entry, task, ProcessingState.COMPLETE);

        assertTrue(resultProcessor.processResults(resultMessage, entry, task));

        List<List<Finding>> findingsByFile = generatedFindings.getAllValues();
        List<Finding> reportFindings = findingsByFile.getLast();
        assertEquals(1, reportFindings.size());
        Finding finding = reportFindings.getFirst();
        assertEquals(RuleName.SSIM_FINTRAFFIC, finding.source());
        assertEquals("invalid_flight", finding.message());
        assertEquals("ERROR", finding.severity());
        assertEquals(task.id(), finding.taskId());
        assertEquals(42L, finding.rulesetId());
        assertNotNull(finding.raw());

        verify(ssimSummaryService).generateSsimInputSummaries(entry, task, tempFiles.get("summary.json"));
    }

    private static Path write(Path directory, String fileName, String content) throws IOException {
        return Files.writeString(directory.resolve(fileName), content);
    }
}
