package fi.digitraffic.tis.vaco.validation.rules.gtfs;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.TestConstants;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.errorhandling.Error;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.errorhandling.ImmutableError;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutablePhase;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableQueueEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.QueueEntry;
import fi.digitraffic.tis.vaco.validation.ValidationService;
import fi.digitraffic.tis.vaco.validation.model.Category;
import fi.digitraffic.tis.vaco.validation.model.FileReferences;
import fi.digitraffic.tis.vaco.validation.model.ImmutableFileReferences;
import fi.digitraffic.tis.vaco.validation.model.ImmutablePhaseData;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationRule;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import fi.digitraffic.tis.vaco.validation.repository.RuleSetsRepository;
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

    private static final Long MOCK_PHASE_ID = 4038721L;
    private static final Long MOCK_VALIDATION_RULE_ID = 2031091L;

    private static final String testBucket = "vaco-test-canonical-gtfs-validator";

    private CanonicalGtfsValidatorRule rule;
    private ImmutableQueueEntry queueEntry;

    private ObjectMapper objectMapper;
    private static VacoProperties vacoProperties;

    @Mock
    private static S3TransferManager s3TransferManager;
    @Mock
    private ErrorHandlerService errorHandlerService;
    @Mock
    private RuleSetsRepository rulesetsRepository;
    @Captor
    private ArgumentCaptor<Error> errorCaptor;
    @Captor
    private ArgumentCaptor<UploadDirectoryRequest> uploadDirRequestCaptor;

    @BeforeAll
    static void beforeAll() {
        vacoProperties = new VacoProperties("test", null, testBucket);
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        rule = new CanonicalGtfsValidatorRule(objectMapper, vacoProperties, s3TransferManager, errorHandlerService, rulesetsRepository);
        queueEntry = ImmutableQueueEntry.builder()
                .format("gtfs")
                .publicId("testPublicId")
                .url("http://nonexistent.url")
                .businessId(TestConstants.FINTRAFFIC_BUSINESS_ID)
                .build();
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(s3TransferManager, errorHandlerService, rulesetsRepository);
    }

    @Test
    void validatesGivenEntry() throws URISyntaxException {
        whenFindValidationRuleByName();
        whenReportError();
        whenDirectoryUpload();

        String testFile = "public/testfiles/padasjoen_kunta.zip";
        ValidationReport report = rule.execute(queueEntry, forInput(testFile)).join();

        assertThat(report.errors().size(), equalTo(51));

        verify(rulesetsRepository, times(51)).findByName(CanonicalGtfsValidatorRule.RULE_NAME);
        verify(s3TransferManager).uploadDirectory(uploadDirRequestCaptor.capture());

        UploadDirectoryRequest request = uploadDirRequestCaptor.getValue();

        assertAll(
                () -> assertTrue(request.source().startsWith(vacoProperties.getTemporaryDirectory()),
                                 "Uploaded directory must reside within system's temporary directory root"),
                () -> assertThat("uses processing bucket from VACO configuration",
                                 request.bucket(), equalTo(vacoProperties.getS3processingBucket())),
                () -> assertThat("S3 prefix contains all important ids so that the outputs get categorized correctly",
                                 request.s3Prefix().get(),
                                 equalTo("entries/testPublicId/phases/" + ValidationService.EXECUTION_PHASE + "/" + CanonicalGtfsValidatorRule.RULE_NAME + "/output"))
        );
    }

    @Test
    void wontAcceptNonGtfsFormatEntries() throws URISyntaxException {
        whenFindValidationRuleByName();
        whenReportError();

        QueueEntry invalidFormat = ImmutableQueueEntry.copyOf(queueEntry).withFormat("vhs");
        ValidationReport report = rule.execute(invalidFormat, forInput("public/testfiles/padasjoen_kunta.zip")).join();

        ImmutableError error = mockError("Wrong format! Expected 'gtfs', was 'vhs'");
        assertThat(report.errors(), equalTo(List.of(error)));

        verify(rulesetsRepository).findByName(CanonicalGtfsValidatorRule.RULE_NAME);
        verify(errorHandlerService).reportError(argThat(equalTo(error)));
    }

    private static void whenDirectoryUpload() {
        DirectoryUpload du = new DefaultDirectoryUpload(CompletableFuture.completedFuture(CompletedDirectoryUpload.builder().build()));
        when(s3TransferManager.uploadDirectory(isA(UploadDirectoryRequest.class))).thenReturn(du);
    }

    private void whenFindValidationRuleByName() {
        when(rulesetsRepository.findByName(CanonicalGtfsValidatorRule.RULE_NAME)).thenReturn(Optional.of(mockValidationRule()));
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
    private static ImmutableValidationRule mockValidationRule() {
        return ImmutableValidationRule.builder()
                .id(MOCK_VALIDATION_RULE_ID)
                .identifyingName(CanonicalGtfsValidatorRule.RULE_NAME)
                .description("injected mock version of the rule")
                .category(Category.GENERIC)
                .build();
    }

    @NotNull
    private ImmutableError mockError(String expectedMessage) {
        return ImmutableError.builder()
                .entryId(queueEntry.id())
                .phaseId(MOCK_PHASE_ID)
                .rulesetId(MOCK_VALIDATION_RULE_ID)
                .message(expectedMessage)
                .build();
    }

    @NotNull
    private ImmutablePhaseData<FileReferences> forInput(String testFile) throws URISyntaxException {
        return ImmutablePhaseData.<FileReferences>builder()
                .phase(ImmutablePhase.builder().id(MOCK_PHASE_ID).name(ValidationService.EXECUTION_PHASE).build())
                .payload(ImmutableFileReferences.builder()
                        .localPath(testResource(testFile))
                        .build())
                .build();
    }

    private Path testResource(String resourceName) throws URISyntaxException {
        URL resource = getClass().getClassLoader().getResource(resourceName);
        return Path.of(Objects.requireNonNull(resource).toURI());
    }
}
