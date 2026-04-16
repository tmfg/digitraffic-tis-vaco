package fi.digitraffic.tis.vaco.rules.results;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.findings.FindingService;
import fi.digitraffic.tis.vaco.findings.model.Finding;
import fi.digitraffic.tis.vaco.findings.model.FindingSeverity;
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
import java.util.concurrent.CompletableFuture;

import static fi.digitraffic.tis.vaco.rules.ResultProcessorTestHelpers.asResultMessage;
import static fi.digitraffic.tis.vaco.rules.ResultProcessorTestHelpers.entryWithTask;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class NetexToGtfsRuleResultProcessorTests extends ResultProcessorTestBase {

    @Mock private S3Client s3Client;
    @Mock private FindingService findingService;
    @Mock private RulesetService rulesetService;

    @Captor private ArgumentCaptor<List<Finding>> capturedFindings;

    private NetexToGtfsRuleResultProcessor resultProcessor;
    private Entry entry;
    private Task task;
    private ImmutableRuleset netex2gtfsRuleset;

    private final Map<String, Path> tempFiles = new HashMap<>();

    @BeforeEach
    void setUp() {
        VacoProperties vacoProperties = TestObjects.vacoProperties();
        resultProcessor = new NetexToGtfsRuleResultProcessor(
            vacoProperties,
            packagesService,
            s3Client,
            taskService,
            findingService,
            rulesetService,
            new ObjectMapper()) {
            @Override
            protected Path downloadFile(Map<String, String> fileNames, String fileName, Path outputDir) {
                return tempFiles.get(fileName);
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
            .withId(42L);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(findingService, rulesetService);
    }

    @Test
    void stdoutLog_withInfoLevelExceptionLines_producesNoFailureFindings(@TempDir Path tempDir) throws IOException {
        Path stdoutLog = tempDir.resolve("stdout.log");
        Files.writeString(stdoutLog,
            "23:28.742 [main] INFO org.onebusaway.gtfs.serialization.GtfsWriter -- writing entities: org.onebusaway.gtfs.model.RouteNameException\n" +
            "23:28.742 [main] INFO org.onebusaway.gtfs.serialization.GtfsWriter -- writing entities: org.onebusaway.gtfs.model.DirectionNameException\n" +
            "23:28.742 [main] INFO org.onebusaway.gtfs.serialization.GtfsWriter -- writing entities: org.onebusaway.gtfs.model.AlternateStopNameException\n");
        Path stderrLog = tempDir.resolve("stderr.log");
        Files.writeString(stderrLog, "");
        tempFiles.put("stdout.log", stdoutLog);
        tempFiles.put("stderr.log", stderrLog);
        ResultMessage resultMessage = asResultMessage(TestObjects.vacoProperties(), RuleName.NETEX2GTFS_ENTUR, entry, Map.of(
            "stdout.log", List.of("debug"),
            "stderr.log", List.of("debug"),
            "result.zip", List.of("result")
        ));
        given(rulesetService.findByName(RuleName.NETEX2GTFS_ENTUR)).willReturn(Optional.of(netex2gtfsRuleset));
        given(findingService.reportFindings(capturedFindings.capture())).willReturn(true);
        given(s3Client.copyFile(any(), any(), any(), any())).willReturn(CompletableFuture.completedFuture(null));

        resultProcessor.processResults(resultMessage, entry, task);

        assertThat(capturedFindings.getValue(), empty());
    }

    @Test
    void stdoutLog_withErrorLevelExceptionLine_producesFailureFinding(@TempDir Path tempDir) throws IOException {
        Path stdoutLog = tempDir.resolve("stdout.log");
        Files.writeString(stdoutLog,
            "12:00.000 [main] ERROR org.onebusaway.SomeClass -- OperatingPeriodException: period not found\n");
        Path stderrLog = tempDir.resolve("stderr.log");
        Files.writeString(stderrLog, "");
        tempFiles.put("stdout.log", stdoutLog);
        tempFiles.put("stderr.log", stderrLog);
        ResultMessage resultMessage = asResultMessage(TestObjects.vacoProperties(), RuleName.NETEX2GTFS_ENTUR, entry, Map.of(
            "stdout.log", List.of("debug"),
            "stderr.log", List.of("debug"),
            "result.zip", List.of("result")
        ));
        given(rulesetService.findByName(RuleName.NETEX2GTFS_ENTUR)).willReturn(Optional.of(netex2gtfsRuleset));
        given(findingService.reportFindings(capturedFindings.capture())).willReturn(true);
        given(s3Client.copyFile(any(), any(), any(), any())).willReturn(CompletableFuture.completedFuture(null));

        resultProcessor.processResults(resultMessage, entry, task);

        List<Finding> findings = capturedFindings.getValue();
        assertThat(findings, hasSize(1));
        assertThat(findings.get(0).severity(), equalTo(FindingSeverity.FAILURE));
    }
}
