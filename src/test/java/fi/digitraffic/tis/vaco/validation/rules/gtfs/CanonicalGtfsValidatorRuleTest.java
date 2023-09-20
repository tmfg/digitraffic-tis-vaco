package fi.digitraffic.tis.vaco.validation.rules.gtfs;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.aws.S3Artifact;
import fi.digitraffic.tis.vaco.errorhandling.Error;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.errorhandling.ImmutableError;
import fi.digitraffic.tis.vaco.process.model.ImmutableTaskData;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.rules.validation.gtfs.CanonicalGtfsValidatorRule;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.validation.ValidationService;
import fi.digitraffic.tis.vaco.validation.model.FileReferences;
import fi.digitraffic.tis.vaco.validation.model.ImmutableFileReferences;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.internal.model.DefaultDirectoryUpload;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryUpload;
import software.amazon.awssdk.transfer.s3.model.DirectoryUpload;
import software.amazon.awssdk.transfer.s3.model.UploadDirectoryRequest;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

@ExtendWith(MockitoExtension.class)
class CanonicalGtfsValidatorRuleTest {

    private static final Long MOCK_TASK_ID = 4038721L;
    private static final Long MOCK_VALIDATION_RULE_ID = 2031091L;

    private static final String testBucket = "vaco-test-canonical-gtfs-validator";

    private CanonicalGtfsValidatorRule rule;
    private ImmutableEntry queueEntry;

    private ObjectMapper objectMapper;
    private static VacoProperties vacoProperties;

    @Mock
    private static S3TransferManager s3TransferManager;
    @Mock
    private ErrorHandlerService errorHandlerService;
    @Mock
    private RulesetRepository rulesetRepository;
    @Captor
    private ArgumentCaptor<Error> errorCaptor;
    @Captor
    private ArgumentCaptor<UploadDirectoryRequest> uploadDirRequestCaptor;

    @BeforeAll
    static void beforeAll() {
        vacoProperties = new VacoProperties("test", null, testBucket, null);
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        rule = new CanonicalGtfsValidatorRule(objectMapper, vacoProperties, s3TransferManager, errorHandlerService, rulesetRepository);
        queueEntry = TestObjects.anEntry("gtfs").build();
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(s3TransferManager, errorHandlerService, rulesetRepository);
    }

    @Test
    void validatesGivenEntry() throws URISyntaxException {
        whenFindValidationRuleByName();
        whenReportError();
        whenDirectoryUpload();

        String testFile = "public/testfiles/padasjoen_kunta.zip";
        ValidationReport report = rule.execute(queueEntry, Optional.empty(), forInput(testFile)).join();

        assertThat(report.errors().size(), equalTo(51));

        verify(rulesetRepository, times(51)).findByName(CanonicalGtfsValidatorRule.RULE_NAME);
        verify(s3TransferManager).uploadDirectory(uploadDirRequestCaptor.capture());

        UploadDirectoryRequest request = uploadDirRequestCaptor.getValue();

        assertAll(
                () -> assertTrue(request.source().startsWith(vacoProperties.getTemporaryDirectory()),
                                 "Uploaded directory must reside within system's temporary directory root"),
                () -> assertThat("uses processing bucket from VACO configuration",
                                 request.bucket(), equalTo(vacoProperties.getS3processingBucket())),
                () -> assertThat("S3 prefix contains all important ids so that the outputs get categorized correctly",
                                 request.s3Prefix().get(),
                                 equalTo(S3Artifact.getValidationTaskPath("testPublicId", ValidationService.EXECUTION_SUBTASK, CanonicalGtfsValidatorRule.RULE_NAME)))
        );
    }

    @Test
    void wontAcceptNonGtfsFormatEntries() throws URISyntaxException {
        whenFindValidationRuleByName();
        whenReportError();

        Entry invalidFormat = ImmutableEntry.copyOf(queueEntry).withFormat("vhs");
        ValidationReport report = rule.execute(invalidFormat, Optional.empty(), forInput("public/testfiles/padasjoen_kunta.zip")).join();

        ImmutableError error = mockError("Wrong format! Expected 'gtfs', was 'vhs'");
        assertThat(report.errors(), equalTo(List.of(error)));

        verify(rulesetRepository).findByName(CanonicalGtfsValidatorRule.RULE_NAME);
        verify(errorHandlerService).reportError(argThat(equalTo(error)));
    }

    private static void whenDirectoryUpload() {
        DirectoryUpload du = new DefaultDirectoryUpload(CompletableFuture.completedFuture(CompletedDirectoryUpload.builder().build()));
        when(s3TransferManager.uploadDirectory(isA(UploadDirectoryRequest.class))).thenReturn(du);
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
                .entryId(queueEntry.id())
                .taskId(MOCK_TASK_ID)
                .rulesetId(MOCK_VALIDATION_RULE_ID)
                .message(expectedMessage)
                .build();
    }

    @NotNull
    private ImmutableTaskData<FileReferences> forInput(String testFile) throws URISyntaxException {
        return ImmutableTaskData.<FileReferences>builder()
            .task(TestObjects.aTask()
                .id(MOCK_TASK_ID)
                .name(ValidationService.EXECUTION_SUBTASK)
                .build())
            .payload(ImmutableFileReferences.of(testResource(testFile)))
            .build();
    }

    private Path testResource(String resourceName) throws URISyntaxException {
        URL resource = getClass().getClassLoader().getResource(resourceName);
        return Path.of(Objects.requireNonNull(resource).toURI());
    }
}
