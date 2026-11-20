package fi.digitraffic.tis.vaco.rules.results;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.guava.GuavaModule;
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

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static fi.digitraffic.tis.vaco.rules.ResultProcessorTestHelpers.asResultMessage;
import static fi.digitraffic.tis.vaco.rules.ResultProcessorTestHelpers.entryWithTask;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class GbfsEnturResultProcessorTests extends ResultProcessorTestBase {

    private GbfsEnturResultProcessor resultProcessor;
    private VacoProperties vacoProperties;
    private ObjectMapper objectMapper;
    @Mock private S3Client s3Client;
    @Mock private FindingService findingService;
    @Mock private RulesetService rulesetService;

    private Entry entry;
    private Task task;
    private Ruleset gbfsEnturRuleset;

    @Captor private ArgumentCaptor<List<Finding>> generatedFindings;

    @BeforeEach
    void setUp() {
        vacoProperties = TestObjects.vacoProperties();
        objectMapper = JsonMapper.builder().addModule(new GuavaModule()).build();
        resultProcessor = new GbfsEnturResultProcessor(vacoProperties,
            packagesService,
            s3Client,
            taskService,
            findingService,
            objectMapper,
            rulesetService) {
            @Override
            protected Path downloadFile(Map<String, String> fileNames, String fileName, Path outputDir) {
                try {
                    Path originalFile = Path.of(Thread.currentThread()
                        .getContextClassLoader()
                        .getResource("rule/results/gbfs/" + fileName)
                        .toURI());

                    Path tempDir = Files.createTempDirectory("tempdir_");
                    Path target = tempDir.resolve(originalFile.getFileName());
                    return Files.copy(originalFile, target);
                } catch (URISyntaxException | IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        entry = entryWithTask(
            e -> ImmutableTask.of(RuleName.GBFS_ENTUR, 100)
                .withId(9_000_000L)
                .withPublicId(NanoIdUtils.randomNanoId()));
        task = entry.tasks().get(0);

        gbfsEnturRuleset = ImmutableRuleset.of(
            RuleName.GBFS_ENTUR,
            "gbfs-entur",
            Category.GENERIC,
            RulesetType.VALIDATION_SYNTAX,
            TransitDataFormat.GBFS);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(packagesService, s3Client, taskService, findingService, rulesetService);
    }

    /**
     * The fixture is trimmed and anonymised validator output where some errors have a null {@code schemaPath}.
     */
    @Test
    void mapsAllErrorsToFindingsIncludingThoseWithoutSchemaPath() {
        ResultMessage resultMessage = asResultMessage(vacoProperties, RuleName.GBFS_ENTUR, entry, Map.of(
            "reports.json", List.of("report"),
            "stdout.log", List.of("debug"),
            "stderr.log", List.of("debug")
        ));

        givenPackageIsCreated("report", entry, task).willReturn(ImmutablePackage.of(task, "all", IGNORED_PATH_VALUE));
        givenPackageIsCreated("debug", entry, task).willReturn(ImmutablePackage.of(task, "debug", IGNORED_PATH_VALUE));
        given(rulesetService.findByName(RuleName.GBFS_ENTUR)).willReturn(Optional.of(gbfsEnturRuleset));
        given(findingService.reportFindings(generatedFindings.capture())).willReturn(true);
        given(findingService.summarizeFindingsSeverities(task)).willReturn(Map.of());
        givenTaskStatusIsMarkedAs(entry, Status.SUCCESS);
        givenTaskProcessingStateIsMarkedAs(entry, task, ProcessingState.COMPLETE);

        resultProcessor.processResults(resultMessage, entry, task);

        // reportFindings is also called with empty lists for the log files
        List<Finding> findings = generatedFindings.getAllValues().stream().flatMap(List::stream).toList();
        List<JsonNode> rawErrors = findings.stream().map(this::parseRaw).toList();

        assertThat(findings, hasSize(3));

        List<JsonNode> withoutSchemaPath = rawErrors.stream()
            .filter(e -> e.has("schemaPath") && e.get("schemaPath").isNull())
            .toList();
        assertThat(withoutSchemaPath, hasSize(2));

        List<String> schemaPaths = rawErrors.stream()
            .filter(e -> e.hasNonNull("schemaPath"))
            .map(e -> e.get("schemaPath").asString())
            .toList();
        assertThat(schemaPaths, equalTo(List.of("#/properties/data/properties/bikes/items/properties/is_disabled")));

        List<String> messages = findings.stream().map(Finding::message).toList();
        assertThat(messages, hasItem("abc123 is not a valid enum value"));
        assertThat(messages, hasItem("plan3 is not a valid enum value"));
    }

    private JsonNode parseRaw(Finding finding) {
        return objectMapper.readTree(finding.raw());
    }
}
