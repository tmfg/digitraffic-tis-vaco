package fi.digitraffic.tis.vaco.rules.results;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import tools.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.findings.FindingService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class Ssim2NetexFintrafficResultProcessorTests extends ResultProcessorTestBase {

    @Mock private S3Client s3Client;
    @Mock private FindingService findingService;
    @Mock private RulesetService rulesetService;

    private Ssim2NetexFintrafficResultProcessor resultProcessor;
    private VacoProperties vacoProperties;
    private Entry entry;
    private Task task;
    private ImmutableRuleset ssim2NetexRuleset;
    private final Map<String, Path> tempFiles = new HashMap<>();

    @BeforeEach
    void setUp() {
        vacoProperties = TestObjects.vacoProperties();
        resultProcessor = new Ssim2NetexFintrafficResultProcessor(
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
        entry = entryWithTask(e -> ImmutableTask.of(RuleName.SSIM2NETEX_FINTRAFFIC, 100)
            .withId(9_000_000L)
            .withPublicId(NanoIdUtils.randomNanoId()));
        task = entry.tasks().getFirst();
        ssim2NetexRuleset = ImmutableRuleset.of(
                RuleName.SSIM2NETEX_FINTRAFFIC,
                "SSIM to NeTEx conversion",
                Category.GENERIC,
                RulesetType.CONVERSION_SYNTAX,
                TransitDataFormat.SSIM)
            .withId(42L);
    }

    @Test
    void completesSuccessfullyWhenResultPackageIsPresent(@TempDir Path tempDir) throws IOException {
        ResultMessage resultMessage = resultMessageWithResultPackage();
        tempFiles.put("stdout.log", write(tempDir, "stdout.log", ""));
        tempFiles.put("stderr.log", write(tempDir, "stderr.log", ""));
        givenPackageIsCreated("result", entry, task).willReturn(ImmutablePackage.of(task, "result", IGNORED_PATH_VALUE));
        givenPackageIsCreated("debug", entry, task).willReturn(ImmutablePackage.of(task, "debug", IGNORED_PATH_VALUE));
        given(rulesetService.findByName(RuleName.SSIM2NETEX_FINTRAFFIC)).willReturn(Optional.of(ssim2NetexRuleset));
        given(findingService.reportFindings(org.mockito.ArgumentMatchers.anyList())).willReturn(true);
        given(findingService.summarizeFindingsSeverities(task)).willReturn(Map.of());
        givenTaskStatusIsMarkedAs(entry, Status.SUCCESS);
        givenTaskProcessingStateIsMarkedAs(entry, task, ProcessingState.COMPLETE);

        assertTrue(resultProcessor.processResults(resultMessage, entry, task));
        verify(taskService).markStatus(entry, task, Status.SUCCESS);
        verify(taskService).trackTask(entry, task, ProcessingState.COMPLETE);
    }

    @Test
    void failsWhenResultPackageIsMissing(@TempDir Path tempDir) throws IOException {
        ResultMessage resultMessage = asResultMessage(vacoProperties, RuleName.SSIM2NETEX_FINTRAFFIC, entry, Map.of(
            "stdout.log", List.of("debug"),
            "stderr.log", List.of("debug")
        ));
        tempFiles.put("stdout.log", write(tempDir, "stdout.log", ""));
        tempFiles.put("stderr.log", write(tempDir, "stderr.log", ""));
        givenPackageIsCreated("debug", entry, task).willReturn(ImmutablePackage.of(task, "debug", IGNORED_PATH_VALUE));
        given(rulesetService.findByName(RuleName.SSIM2NETEX_FINTRAFFIC)).willReturn(Optional.of(ssim2NetexRuleset));
        given(findingService.reportFindings(org.mockito.ArgumentMatchers.anyList())).willReturn(true);
        givenTaskStatusIsMarkedAs(entry, Status.FAILED);
        givenTaskProcessingStateIsMarkedAs(entry, task, ProcessingState.COMPLETE);

        assertFalse(resultProcessor.processResults(resultMessage, entry, task));
        verify(taskService).markStatus(entry, task, Status.FAILED);
        verify(taskService).trackTask(entry, task, ProcessingState.COMPLETE);
    }

    private ResultMessage resultMessageWithResultPackage() {
        return asResultMessage(vacoProperties, RuleName.SSIM2NETEX_FINTRAFFIC, entry, Map.of(
            "converted.xml", List.of("result"),
            "stdout.log", List.of("debug"),
            "stderr.log", List.of("debug")
        ));
    }

    private static Path write(Path directory, String fileName, String content) throws IOException {
        return Files.writeString(directory.resolve(fileName), content);
    }
}
