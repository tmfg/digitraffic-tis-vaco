package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestConstants;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableQueueEntry;
import fi.digitraffic.tis.vaco.queuehandler.repository.QueueHandlerRepository;
import fi.digitraffic.tis.vaco.validation.model.Category;
import fi.digitraffic.tis.vaco.validation.model.FileReferences;
import fi.digitraffic.tis.vaco.validation.model.ImmutablePhaseData;
import fi.digitraffic.tis.vaco.validation.model.ImmutableResult;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationReport;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationRule;
import fi.digitraffic.tis.vaco.validation.model.Result;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import fi.digitraffic.tis.vaco.validation.rules.Rule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

@Import(ValidationServiceIntegrationTests.ContextConfiguration.class)
class ValidationServiceIntegrationTests extends SpringBootIntegrationTestBase {

    public static final String TEST_PHASE_NAME = "test.helloworld";
    public static final String TEST_RULE_NAME = "hello world";
    public static final String TEST_RULE_RESULT = "The world was greeted";

    @TestConfiguration
    public static class ContextConfiguration {

        @Bean
        public Rule monitoringService() {
            return new Rule() {
                @Override
                public String getIdentifyingName() {
                    return TEST_RULE_NAME;
                }

                @Override
                public CompletableFuture<Result<ValidationReport>> execute(ImmutablePhaseData<FileReferences> phaseData) {
                    return CompletableFuture.completedFuture(
                            ImmutableResult.of(
                                    TEST_PHASE_NAME,
                                    ImmutableValidationReport.of(TEST_RULE_NAME, TEST_RULE_RESULT)));
                }
            };
        }
    }

    @Autowired
    private VacoProperties vacoProperties;

    @Autowired
    private QueueHandlerRepository queueHandlerRepository;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private S3TransferManager s3TransferManager;

    @MockBean
    private HttpClient httpClient;

    @Captor
    private ArgumentCaptor<HttpRequest> requestCaptor;

    @Captor
    private ArgumentCaptor<HttpResponse.BodyHandler<Path>> bodyHandlerCaptor;

    @Mock
    HttpResponse<Path> response;

    @Test
    void uploadsDownloadedFileToS3() throws URISyntaxException, IOException {
        when(httpClient.sendAsync(requestCaptor.capture(), bodyHandlerCaptor.capture())).thenReturn(CompletableFuture.supplyAsync(() -> response));
        when(response.body()).thenReturn(Path.of(ClassLoader.getSystemResource("integration/validation/smallfile.txt").toURI()));

        ImmutableQueueEntry entry = createQueueEntryForTesting();
        s3Client.createBucket(CreateBucketRequest.builder().bucket(vacoProperties.getS3processingBucket()).build());

        validationService.downloadFile(entry);

        assertThat(requestCaptor.getValue().uri(), equalTo(new URI("https://testfile")));

        List<S3Object> uploadedFiles = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(vacoProperties.getS3processingBucket())
                        .prefix("entries/" + entry.publicId())
                        .build())
                .contents();

        assertThat(uploadedFiles.size(), equalTo(1));

        String key = uploadedFiles.get(0).key();
        Path redownload = Paths.get(vacoProperties.getTemporaryDirectory(), "testfile");
        s3TransferManager.downloadFile(DownloadFileRequest.builder()
                        .getObjectRequest(req -> req.bucket(vacoProperties.getS3processingBucket()).key(key))
                        .destination(redownload)
                        .build())
                .completionFuture()
                .join();
        assertThat(Files.readString(redownload), equalTo("I'm smol :3\n"));

        Files.delete(redownload);
    }

    private ImmutableQueueEntry createQueueEntryForTesting() {
        return queueHandlerRepository.create(
                ImmutableQueueEntry.builder()
                        .format("huuhaa")
                        .url("https://testfile")
                        .businessId(TestConstants.FINTRAFFIC_BUSINESS_ID)
                        .build());
    }

    @Test
    void executesRulesBasedOnIdentifyingName() {
        ImmutableQueueEntry entry = createQueueEntryForTesting();
        List<Result<ValidationReport>> results = validationService.executeRules(entry, null,
                Set.of(ImmutableValidationRule.builder()
                        .identifyingName(TEST_RULE_NAME)
                        .description("running hello rule from tests")
                        .category(Category.SPECIFIC)
                        .build()));

        assertThat(results, equalTo(List.of(
                ImmutableResult.of(
                        TEST_PHASE_NAME,
                        ImmutableValidationReport.of(TEST_RULE_NAME, TEST_RULE_RESULT)))));
    }
}
