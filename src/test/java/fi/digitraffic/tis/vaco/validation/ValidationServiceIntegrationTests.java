package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.http.HttpClient;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.aws.S3Artifact;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.queuehandler.repository.QueueHandlerRepository;
import fi.digitraffic.tis.vaco.rules.Rule;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationReport;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

@Import(ValidationServiceIntegrationTests.ContextConfiguration.class)
class ValidationServiceIntegrationTests extends SpringBootIntegrationTestBase {

    public static final String TEST_RULE_NAME = "hello world";
    public static final String TEST_RULE_RESULT = "The world was greeted";

    private static AtomicReference<ValidationReport> ruleReport = new AtomicReference<>();

    @Autowired
    private QueueHandlerService queueHandlerService;

    @TestConfiguration
    public static class ContextConfiguration {

        @Bean
        @Qualifier("validation")
        public Rule<ValidationInput, ValidationReport> monitoringService() {
            return new Rule<>() {
                @Override
                public String getIdentifyingName() {
                    return TEST_RULE_NAME;
                }

                @Override
                public CompletableFuture<ValidationReport> execute(Entry entry, Task task, S3Path input, Optional<ValidationInput> configuration) {
                    ImmutableValidationReport report = ImmutableValidationReport.of(TEST_RULE_RESULT);
                    ruleReport.set(report);
                    return CompletableFuture.completedFuture(report);
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
    private HttpClient httpClientUtility;

    @Captor
    private ArgumentCaptor<Path> filePath;

    @Captor
    private ArgumentCaptor<String> entryUrl;

    @Captor
    private ArgumentCaptor<String> entryEtag;

    @Mock
    HttpResponse<Path> response;

    @Test
    void uploadsDownloadedFileToS3() throws URISyntaxException, IOException {
        when(httpClientUtility.downloadFile(filePath.capture(), entryUrl.capture(), entryEtag.capture()))
            .thenReturn(CompletableFuture.supplyAsync(() -> response));
        when(response.body()).thenReturn(Path.of(ClassLoader.getSystemResource("integration/validation/smallfile.txt").toURI()));

        ImmutableEntry entry = createQueueEntryForTesting();
        s3Client.createBucket(CreateBucketRequest.builder().bucket(vacoProperties.getS3ProcessingBucket()).build());

        validationService.downloadFile(entry);

        assertThat(entryUrl.getValue(), equalTo("https://testfile"));

        List<S3Object> uploadedFiles = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(vacoProperties.getS3ProcessingBucket())
                        .prefix("entries/" + entry.publicId())
                        .build())
                .contents();

        assertThat(uploadedFiles.size(), equalTo(1));

        String key = uploadedFiles.get(0).key();
        Path redownload = Paths.get(vacoProperties.getTemporaryDirectory(), "testfile");
        s3TransferManager.downloadFile(DownloadFileRequest.builder()
                        .getObjectRequest(req -> req.bucket(vacoProperties.getS3ProcessingBucket()).key(key))
                        .destination(redownload)
                        .build())
                .completionFuture()
                .join();
        assertThat(Files.readString(redownload), equalTo("I'm smol :3\n"));

        Files.delete(redownload);
    }

    private ImmutableEntry createQueueEntryForTesting() {
        return queueHandlerRepository.create(TestObjects.anEntry("gtfs").build());
    }

    @Test
    void executesRulesBasedOnIdentifyingName() {
        ImmutableEntry entry = createQueueEntryForTesting();
        S3Path s3TargetPath = ImmutableS3Path.of(S3Artifact.getTaskPath(entry.publicId(), ValidationService.DOWNLOAD_SUBTASK).path() + "/" + entry.format() + ".original");
        validationService.executeRules(
            entry,
            s3TargetPath,
            Set.of(TestObjects.aRuleset()
                    .identifyingName(TEST_RULE_NAME)
                    .description("running hello rule from tests")
                    .category(Category.SPECIFIC)
                    .build()));

        assertThat(ruleReport.get(), equalTo(ImmutableValidationReport.of(TEST_RULE_RESULT)));
    }
}
