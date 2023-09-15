package fi.digitraffic.tis.vaco.validation.rules.gtfs;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.AwsIntegrationTestBase;
import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.errorhandling.Error;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.errorhandling.ImmutableError;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.model.ImmutableRuleExecutionJobMessage;
import fi.digitraffic.tis.vaco.rules.model.RuleExecutionJobMessage;
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
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

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
    @Captor
    private ArgumentCaptor<Error> errorCaptor;

    @BeforeAll
    static void beforeAll() throws IOException {
        // create input directory which matches the behavior of ValidatorService
        testInputDir = testDirectory.resolve("input");
        Files.createDirectories(testInputDir);
        vacoProperties = new VacoProperties("test", null, testBucket);
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        s3Client = new S3Client(vacoProperties, s3TransferManager, awsS3Client);
        rule = new CanonicalGtfsValidatorRule(objectMapper, vacoProperties, errorHandlerService, rulesetRepository, s3Client, packagesService);
        entry = TestObjects.anEntry("gtfs").build();
        task = TestObjects.aTask().id(MOCK_TASK_ID).entryId(entry.id()).build();
        createBucket(vacoProperties.getS3ProcessingBucket());
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(errorHandlerService, rulesetRepository, packagesService);
    }

    @Test
    void validatesGivenEntry() throws URISyntaxException, IOException {
        // XXX: This {format}.download would be nice to express in some more type safe manner
        givenTestFile("public/testfiles/padasjoen_kunta.zip", ImmutableS3Path.of(s3Input + "/" + entry.format() + ".download"));
        whenFindValidationRuleByName();
        whenReportError();

        RuleExecutionJobMessage<ValidationInput> message = ImmutableRuleExecutionJobMessage.<ValidationInput>builder()
            .entry(entry)
            .task(task)
            .workDirectory(s3Input.toString())
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
        ValidationReport report = rule.execute(message).join();

        assertThat(report.errors().size(), equalTo(51));

        verify(rulesetRepository, times(51)).findByName(CanonicalGtfsValidatorRule.RULE_NAME);
        verify(packagesService).createPackage(
            eq(entry),
            eq(task),
            eq(CanonicalGtfsValidatorRule.RULE_NAME),
            eq(ImmutableS3Path.of("entries/" + entry.publicId() + "/tasks/" + task.name() + "/rules/gtfs.canonical.v4_0_0/output")),
            eq("content.zip"));
    }

    @Test
    void wontAcceptNonGtfsFormatEntries() throws URISyntaxException {
        whenFindValidationRuleByName();
        whenReportError();

        Entry invalidFormat = ImmutableEntry.copyOf(entry).withFormat("vhs");
        RuleExecutionJobMessage<ValidationInput> message = ImmutableRuleExecutionJobMessage.<ValidationInput>builder()
            .entry(invalidFormat)
            .task(task)
            .workDirectory(ImmutableS3Path.of(forInput("public/testfiles/padasjoen_kunta.zip").toString()).toString())
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
        ValidationReport report = rule.execute(message).join();

        ImmutableError error = mockError("Wrong format! Expected 'gtfs', was 'vhs'");
        assertThat(report.errors(), equalTo(List.of(error)));

        verify(rulesetRepository).findByName(CanonicalGtfsValidatorRule.RULE_NAME);
        verify(errorHandlerService).reportError(argThat(equalTo(error)));
    }

    private void whenFindValidationRuleByName() {
        when(rulesetRepository.findByName(CanonicalGtfsValidatorRule.RULE_NAME)).thenReturn(Optional.of(mockValidationRule()));
    }

    private void whenReportError() {
        doAnswer(voidCall()).when(errorHandlerService).reportError(errorCaptor.capture());
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
                .identifyingName(CanonicalGtfsValidatorRule.RULE_NAME)
                .description("injected mock version of the rule")
                .build();
    }

    @NotNull
    private ImmutableError mockError(String expectedMessage) {
        return ImmutableError.builder()
                .entryId(entry.id())
                .taskId(MOCK_TASK_ID)
                .rulesetId(MOCK_VALIDATION_RULE_ID)
                .message(expectedMessage)
                .build();
    }

    private void givenTestFile(String file, S3Path target) throws URISyntaxException, IOException {
        URL resource = CanonicalGtfsValidatorRuleTest.class.getClassLoader().getResource(file);
        s3Client.uploadFile(vacoProperties.getS3ProcessingBucket(), target, Path.of(resource.toURI())).join();
    }

    private Path forInput(String testFile) throws URISyntaxException {
        URL resource = CanonicalGtfsValidatorRuleTest.class.getClassLoader().getResource(testFile);
        return Path.of(Objects.requireNonNull(resource).toURI()).getParent();
    }

}
