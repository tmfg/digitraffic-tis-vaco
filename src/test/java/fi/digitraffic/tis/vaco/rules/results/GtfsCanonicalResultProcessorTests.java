package fi.digitraffic.tis.vaco.rules.results;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
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
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.RulesetType;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static fi.digitraffic.tis.vaco.rules.ResultProcessorTestHelpers.asResultMessage;
import static fi.digitraffic.tis.vaco.rules.ResultProcessorTestHelpers.entryWithTask;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class GtfsCanonicalResultProcessorTests extends ResultProcessorTestBase {

    private GtfsCanonicalResultProcessor resultProcessor;
    private VacoProperties vacoProperties;
    private ObjectMapper objectMapper;
    @Mock private S3Client s3Client;
    @Mock private FindingService findingService;
    @Mock private RulesetService rulesetService;

    private ResultMessage resultMessage;
    private Entry entry;
    private Task task;
    private Ruleset gtfsCanonicalRuleset;

    @Captor private ArgumentCaptor<List<Finding>> generatedFindings;

    @BeforeEach
    void setUp() {
        vacoProperties = TestObjects.vacoProperties();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new GuavaModule());
        resultProcessor = new GtfsCanonicalResultProcessor(vacoProperties,
            packagesService,
            s3Client,
            taskService,
            findingService,
            rulesetService,
            objectMapper) {
            @Override
            protected Path downloadFile(Map<String, String> fileNames, String fileName, Path outputDir) {
                try {
                    return Path.of(Thread.currentThread()
                        .getContextClassLoader()
                        .getResource("rule/results/gtfs/" + fileName)
                        .toURI());
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        entry = entryWithTask(e -> ImmutableTask.of(RuleName.GTFS_CANONICAL, 100).withId(9_000_000L));
        task = entry.tasks().get(0);

        gtfsCanonicalRuleset = ImmutableRuleset.of(
                RuleName.GTFS_CANONICAL,
                "bleh",
                Category.GENERIC,
                RulesetType.VALIDATION_SYNTAX,
                TransitDataFormat.GTFS)
            .withBeforeDependencies(List.of(DownloadRule.PREPARE_DOWNLOAD_TASK));
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(packagesService, s3Client, taskService, findingService, rulesetService);
    }

    @Test
    void mapsGtfsCanonicalNoticesToFindings() {
        resultMessage = asResultMessage(vacoProperties, RuleName.GTFS_CANONICAL, entry, Map.of("report.json", List.of("report")));

        givenPackageIsCreated("report", entry, task).willReturn(ImmutablePackage.of(task, "all", IGNORED_PATH_VALUE));
        given(rulesetService.findByName(RuleName.GTFS_CANONICAL)).willReturn(Optional.of(gtfsCanonicalRuleset));
        given(findingService.reportFindings(generatedFindings.capture())).willReturn(true);
        given(findingService.findFindingsByName(task, "thread_execution_error")).willReturn(List.of());
        given(findingService.summarizeFindingsSeverities(task)).willReturn(Map.of());
        givenTaskStatusIsMarkedAs(entry, Status.SUCCESS);
        givenTaskProcessingStateIsMarkedAs(entry, task, ProcessingState.COMPLETE);

        resultProcessor.processResults(resultMessage, entry, task);

        List<Finding> findings = generatedFindings.getValue();

        // TODO: chore for the bored yet excited: better assertions for these
        assertThat(findings.size(), equalTo(51));
        findings.forEach(f -> assertThat(f.source(), equalTo(RuleName.GTFS_CANONICAL)));
    }

    @Test
    void mapsGtfsCanonicalSystemErrorsToFindings() {
        resultMessage = asResultMessage(vacoProperties, RuleName.GTFS_CANONICAL, entry, Map.of("system_errors.json", List.of("report")));

        givenPackageIsCreated("report", entry, task).willReturn(ImmutablePackage.of(task, "all", IGNORED_PATH_VALUE));
        given(rulesetService.findByName(RuleName.GTFS_CANONICAL)).willReturn(Optional.of(gtfsCanonicalRuleset));
        given(findingService.reportFindings(generatedFindings.capture())).willReturn(true);
        given(findingService.findFindingsByName(task, "thread_execution_error")).willReturn(List.of());
        given(findingService.summarizeFindingsSeverities(task)).willReturn(Map.of());
        givenTaskStatusIsMarkedAs(entry, Status.SUCCESS);
        givenTaskProcessingStateIsMarkedAs(entry, task, ProcessingState.COMPLETE);

        resultProcessor.processResults(resultMessage, entry, task);

        List<Finding> findings = generatedFindings.getValue();

        // TODO: chore for the bored yet excited: better assertions for these
        assertThat(findings.size(), equalTo(1));
        findings.forEach(f -> assertThat(f.source(), equalTo(RuleName.GTFS_CANONICAL)));
    }

    @Test
    void GtfsCanonicalRuntimeErrorGivesWarning() {
        resultMessage = asResultMessage(vacoProperties, RuleName.GTFS_CANONICAL, entry, Map.of("system_errors.json", List.of("report")));

        givenPackageIsCreated("report", entry, task).willReturn(ImmutablePackage.of(task, "all", IGNORED_PATH_VALUE));
        given(rulesetService.findByName(RuleName.GTFS_CANONICAL)).willReturn(Optional.of(gtfsCanonicalRuleset));
        given(findingService.reportFindings(generatedFindings.capture())).willReturn(true);
        given(findingService.findFindingsByName(task, "thread_execution_error")).willReturn(List.of());
        given(findingService.summarizeFindingsSeverities(task)).willReturn(Map.of());
        givenTaskStatusIsMarkedAs(entry, Status.SUCCESS);
        givenTaskProcessingStateIsMarkedAs(entry, task, ProcessingState.COMPLETE);

        resultProcessor.processResults(resultMessage, entry, task);

        List<Finding> findings = generatedFindings.getValue();

        assertThat(findings.size(), equalTo(1));
        Finding finding = findings.get(0);
        assertThat(finding.severity(), equalTo("WARNING"));
        findings.forEach(f -> assertThat(f.source(), equalTo(RuleName.GTFS_CANONICAL)));
    }
}
