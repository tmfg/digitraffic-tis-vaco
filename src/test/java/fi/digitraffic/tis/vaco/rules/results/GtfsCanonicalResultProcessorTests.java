package fi.digitraffic.tis.vaco.rules.results;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.findings.Finding;
import fi.digitraffic.tis.vaco.findings.FindingService;
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
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import fi.digitraffic.tis.vaco.validation.RulesetSubmissionService;
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
import java.util.Random;

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
        objectMapper.registerModules(new GuavaModule());
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
        entry = entryWithTask(e -> ImmutableTask.of(new Random().nextLong(), RuleName.GTFS_CANONICAL, 100).withId(9_000_000L));
        task = entry.tasks().get(0);
        Map<String, List<String>> uploadedFiles = Map.of("report.json", List.of("report"));
        resultMessage = asResultMessage(vacoProperties, RuleName.GTFS_CANONICAL, entry, uploadedFiles);
        gtfsCanonicalRuleset = ImmutableRuleset.of(
                new Random().nextLong(),
                RuleName.GTFS_CANONICAL,
                "bleh",
                Category.GENERIC,
                Type.VALIDATION_SYNTAX,
                TransitDataFormat.GTFS)
            .withDependencies(List.of(DownloadRule.DOWNLOAD_SUBTASK, RulesetSubmissionService.VALIDATE_TASK));
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(packagesService, s3Client, taskService, findingService, rulesetService);
    }

    @Test
    void mapsGtfsCanonicalNoticesToFindings() {
        givenPackageIsCreated("report", entry, task).willReturn(ImmutablePackage.of(task.id(), "all", IGNORED_PATH_VALUE));
        given(rulesetService.findByName(RuleName.GTFS_CANONICAL)).willReturn(Optional.of(gtfsCanonicalRuleset));
        given(findingService.reportFindings(generatedFindings.capture())).willReturn(true);
        given(findingService.summarizeFindingsSeverities(entry, task)).willReturn(Map.of());
        givenTaskStatusIsMarkedAs(entry, Status.SUCCESS);

        resultProcessor.processResults(resultMessage, entry, task);

        List<Finding> findings = generatedFindings.getValue();

        // TODO: chore for the bored yet excited: better assertions for these
        assertThat(findings.size(), equalTo(51));
        findings.forEach(f -> assertThat(f.source(), equalTo(RuleName.GTFS_CANONICAL)));
    }

}
