package fi.digitraffic.tis.vaco.validation.rules.gtfs;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.AwsIntegrationTestBase;
import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.errorhandling.Error;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.errorhandling.ImmutableError;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.model.ImmutableValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.rules.model.ValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.rules.validation.gtfs.CanonicalGtfsValidatorRule;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CanonicalGtfsValidatorRuleTest extends AwsIntegrationTestBase {

    private static final Long MOCK_TASK_ID = 4038721L;
    private static final Long MOCK_VALIDATION_RULE_ID = 2031091L;
    private static final String testBucket = "vaco-test-canonical-gtfs-validator";

    @TempDir
    static Path testDirectory;
    static Path testInputDir;

    static S3Path s3Root = ImmutableS3Path.of("canonical-gtfs-test");
    static S3Path s3Input = ImmutableS3Path.of(s3Root + "/input");
    static S3Path s3Output = ImmutableS3Path.of(s3Root + "/output");

    private CanonicalGtfsValidatorRule rule;
    private ImmutableEntry entry;
    private ImmutableTask task;

    private ObjectMapper objectMapper;
    private static VacoProperties vacoProperties;
    private S3Client s3Client;

    @Mock
    private ErrorHandlerService errorHandlerService;
    @Mock
    private RulesetRepository rulesetRepository;
    @Mock
    private PackagesService packagesService;
    @Mock
    private MessagingService messagingService;
    @Mock
    private TaskService taskService;

    @Captor
    private ArgumentCaptor<List<Error>> errorCaptor;

    @BeforeAll
    static void beforeAll() throws IOException {
        // create input directory which matches the behavior of ValidatorService
        testInputDir = testDirectory.resolve("input");
        Files.createDirectories(testInputDir);
        vacoProperties = new VacoProperties("test", null, testBucket, null, "biz");
        createBucket(vacoProperties.getS3ProcessingBucket());
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        s3Client = new S3Client(vacoProperties, s3TransferManager, awsS3Client);

        rule = new CanonicalGtfsValidatorRule(
            objectMapper,
            vacoProperties,
            errorHandlerService,
            rulesetRepository,
            s3Client,
            packagesService,
            messagingService,
            taskService);
        entry = TestObjects.anEntry("gtfs").build();
        task = TestObjects.aTask().id(MOCK_TASK_ID).entryId(entry.id()).build();
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(errorHandlerService, rulesetRepository, packagesService, messagingService, taskService);
    }

    @Test
    void validatesGivenEntry() throws URISyntaxException {
        givenTestFile("public/testfiles/padasjoen_kunta.zip", s3Input.resolve(entry.format() + ".zip"));
        whenFindValidationRuleByName();
        whenReportErrors();
        whenTaskStateChangesAreTracked();

        ValidationRuleJobMessage message = ImmutableValidationRuleJobMessage.builder()
            .entry(entry)
            .task(task)
            .inputs(s3Input.toString())
            .outputs(s3Output.toString())
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
        ValidationReport report = rule.execute(message).join();

        assertThat(report.errors().size(), equalTo(51));

        verify(rulesetRepository, times(51)).findByName(RuleName.GTFS_CANONICAL_4_0_0);
        verify(packagesService).createPackage(
            eq(entry),
            eq(task),
            eq(RuleName.GTFS_CANONICAL_4_0_0),
            eq(s3Output),
            eq("content.zip"));
    }

    private void whenTaskStateChangesAreTracked() {
        when(taskService.findTask(eq(entry.id()), eq(RuleName.GTFS_CANONICAL_4_0_0))).thenReturn(task);
        when(taskService.trackTask(eq(task), eq(ProcessingState.START))).thenReturn(task);
        when(taskService.trackTask(eq(task), eq(ProcessingState.COMPLETE))).thenReturn(task);
    }

    @Test
    void wontAcceptNonGtfsFormatEntries() throws URISyntaxException {
        whenFindValidationRuleByName();
        whenReportErrors();
        whenTaskStateChangesAreTracked();

        Entry invalidFormat = ImmutableEntry.copyOf(entry).withFormat("vhs");
        ValidationRuleJobMessage message = ImmutableValidationRuleJobMessage.builder()
            .entry(invalidFormat)
            .task(task)
            .inputs(ImmutableS3Path.of(forInput("public/testfiles/padasjoen_kunta.zip").toString()).toString())
            .outputs(ImmutableS3Path.of("just/a/path").toString())
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
        ValidationReport report = rule.execute(message).join();

        ImmutableError error = mockError("Wrong format! Expected 'gtfs', was 'vhs'");
        assertThat(report.errors(), equalTo(List.of(error)));

        verify(rulesetRepository).findByName(RuleName.GTFS_CANONICAL_4_0_0);
    }

    private void whenFindValidationRuleByName() {
        when(rulesetRepository.findByName(RuleName.GTFS_CANONICAL_4_0_0)).thenReturn(Optional.of(mockValidationRule()));
    }

    private void whenReportErrors() {
        doAnswer(voidCall()).when(errorHandlerService).reportErrors(errorCaptor.capture());
    }

    @NotNull
    private static Answer voidCall() {
        return invocation -> {
            Object[] args = invocation.getArguments();
            Object mock = invocation.getMock();
            return null;
        };
    }

    @NotNull
    private static ImmutableRuleset mockValidationRule() {
        return TestObjects.aRuleset()
                .id(MOCK_VALIDATION_RULE_ID)
                .identifyingName(RuleName.GTFS_CANONICAL_4_0_0)
                .description("injected mock version of the rule")
                .build();
    }

    @NotNull
    private ImmutableError mockError(String expectedMessage) {
        return ImmutableError.builder()
            .entryId(entry.publicId())
            .taskId(MOCK_TASK_ID)
            .rulesetId(MOCK_VALIDATION_RULE_ID)
            .source(rule.getIdentifyingName())
            .message(expectedMessage)
            .build();
    }

    private void givenTestFile(String file, S3Path target) throws URISyntaxException {
        URL resource = CanonicalGtfsValidatorRuleTest.class.getClassLoader().getResource(file);
        s3Client.uploadFile(vacoProperties.getS3ProcessingBucket(), target, Path.of(resource.toURI())).join();
    }

    private Path forInput(String testFile) throws URISyntaxException {
        URL resource = CanonicalGtfsValidatorRuleTest.class.getClassLoader().getResource(testFile);
        return Path.of(Objects.requireNonNull(resource).toURI()).getParent();
    }

}
