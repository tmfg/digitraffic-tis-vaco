package fi.digitraffic.tis.vaco.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.http.HttpClient;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableValidationInput;
import fi.digitraffic.tis.vaco.queuehandler.repository.QueueHandlerRepository;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import fi.digitraffic.tis.vaco.rules.model.ValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
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
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

class ValidationServiceIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    private QueueHandlerRepository queueHandlerRepository;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private MessagingService messagingService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskService taskService;

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
    @Autowired
    private DownloadRule downloadRule;

    @BeforeAll
    static void beforeAll(@Autowired VacoProperties vacoProperties) {
        awsS3Client.createBucket(CreateBucketRequest.builder().bucket(vacoProperties.s3ProcessingBucket()).build());
    }

    private Entry createEntryForTesting() {
        return queueHandlerRepository.create(TestObjects.anEntry("gtfs").addValidations(ImmutableValidationInput.of(RuleName.GTFS_CANONICAL_4_0_0)).build());
    }

    @Test
    void delegatesRuleProcessingToRuleSpecificQueueBasedOnRuleName() throws URISyntaxException {
        when(httpClientUtility.downloadFile(filePath.capture(), entryUrl.capture(), entryEtag.capture()))
            .thenReturn(CompletableFuture.supplyAsync(() -> response));
        when(response.body()).thenReturn(Path.of(ClassLoader.getSystemResource("integration/validation/smallfile.txt").toURI()));

        Entry entry = createEntryForTesting();
        String testQueueName = createSqsQueue();
        ResultMessage downloadedFile = downloadRule.execute(entry).join();

        Task task = taskService.findTask(entry.id(), ValidationService.VALIDATE_TASK);
        validationService.executeRules(
            entry,
            task,
            // DownloadRule procudes just a single file so this is OK
            S3Path.of(URI.create(List.copyOf(downloadedFile.uploadedFiles().keySet()).get(0)).getPath()),
            Set.of(TestObjects.aRuleset()
                .identifyingName(RuleName.GTFS_CANONICAL_4_0_0)
                .description("running hello rule from tests")
                .category(Category.SPECIFIC)
                .format(TransitDataFormat.GTFS)
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
        // TODO: fails on purpose, breakpoint + refactor
        S3Path expectedPath = ImmutableS3Path.of(inputUri.getPath() + "/gtfs.zip");

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
