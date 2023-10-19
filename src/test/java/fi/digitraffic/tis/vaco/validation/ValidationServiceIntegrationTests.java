package fi.digitraffic.tis.vaco.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.http.HttpClient;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.repository.QueueHandlerRepository;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.model.ValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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

public class ValidationServiceIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    private VacoProperties vacoProperties;

    @Autowired
    private QueueHandlerRepository queueHandlerRepository;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private MessagingService messagingService;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private ObjectMapper objectMapper;

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

    @BeforeAll
    static void beforeAll(@Autowired VacoProperties vacoProperties) {
        awsS3Client.createBucket(CreateBucketRequest.builder().bucket(vacoProperties.s3ProcessingBucket()).build());
    }

    @Test
    void uploadsDownloadedFileToS3() throws URISyntaxException, IOException {
        when(httpClientUtility.downloadFile(filePath.capture(), entryUrl.capture(), entryEtag.capture()))
            .thenReturn(CompletableFuture.supplyAsync(() -> response));
        when(response.body()).thenReturn(Path.of(ClassLoader.getSystemResource("integration/validation/smallfile.txt").toURI()));

        Entry entry = createEntryForTesting();

        validationService.downloadFile(entry);

        assertThat(entryUrl.getValue(), equalTo("https://testfile"));

        List<S3Object> uploadedFiles = awsS3Client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(vacoProperties.s3ProcessingBucket())
                        .prefix("entries/" + entry.publicId())
                        .build())
                .contents();

        assertThat(uploadedFiles.size(), equalTo(1));

        String key = uploadedFiles.get(0).key();
        Path redownload = Paths.get(vacoProperties.temporaryDirectory(), "testfile");
        s3TransferManager.downloadFile(DownloadFileRequest.builder()
                        .getObjectRequest(req -> req.bucket(vacoProperties.s3ProcessingBucket()).key(key))
                        .destination(redownload)
                        .build())
                .completionFuture()
                .join();
        assertThat(Files.readString(redownload), equalTo("I'm smol :3\n"));

        Files.delete(redownload);
    }

    private Entry createEntryForTesting() {
        return queueHandlerRepository.create(TestObjects.anEntry("gtfs").build());
    }

    @Test
    void delegatesRuleProcessingToRuleSpecificQueueBasedOnRuleName() throws URISyntaxException {
        when(httpClientUtility.downloadFile(filePath.capture(), entryUrl.capture(), entryEtag.capture()))
            .thenReturn(CompletableFuture.supplyAsync(() -> response));
        when(response.body()).thenReturn(Path.of(ClassLoader.getSystemResource("integration/validation/smallfile.txt").toURI()));

        Entry entry = createEntryForTesting();
        String testQueueName = createSqsQueue();
        S3Path downloadedFile = validationService.downloadFile(entry);

        validationService.executeRules(
            entry,
            downloadedFile,
            Set.of(TestObjects.aRuleset()
                    .identifyingName(RuleName.GTFS_CANONICAL_4_0_0)
                    .description("running hello rule from tests")
                    .category(Category.SPECIFIC)
                    .build()));

        List<ValidationRuleJobMessage> messages = messagingService.readMessages(testQueueName).map(m -> {
            try {
                return objectMapper.readValue(m.body(), ValidationRuleJobMessage.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }).toList();

        // only one message is generated
        assertThat(messages.size(), equalTo(1));
        ValidationRuleJobMessage message = messages.get(0);
        // S3 path references are correctly set
        // NOTE: the task name repeats the matching task name on purpose
        assertThat(message.inputs(), equalTo("s3://digitraffic-tis-processing-itest/entries/" + entry.publicId() + "/tasks/" + RuleName.GTFS_CANONICAL_4_0_0 + "/rules/" + RuleName.GTFS_CANONICAL_4_0_0 + "/input"));
        assertThat(message.outputs(), equalTo("s3://digitraffic-tis-processing-itest/entries/" + entry.publicId() + "/tasks/" + RuleName.GTFS_CANONICAL_4_0_0 + "/rules/" + RuleName.GTFS_CANONICAL_4_0_0 + "/output"));
        // downloaded file is copied to inputs
        URI inputUri = URI.create(message.inputs());
        S3Path expectedPath = ImmutableS3Path.of(inputUri.getPath() + "/" + downloadedFile.getLast());

        HeadObjectResponse r = awsS3Client.headObject(HeadObjectRequest.builder()
            .bucket(inputUri.getHost())
            .key(expectedPath.toString())
            .build());
        assertThat(r.contentLength(), equalTo(12L));
    }

    @NotNull
    private static String createSqsQueue() {
        String testQueueName = MessageQueue.RULE_PROCESSING.munge(RuleName.GTFS_CANONICAL_4_0_0);
        CreateQueueResponse r = sqsClient.createQueue(CreateQueueRequest.builder().queueName(testQueueName).build());
        return testQueueName;
    }

}
