package fi.digitraffic.tis.vaco.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.http.VacoHttpClient;
import fi.digitraffic.tis.vaco.http.model.DownloadResponse;
import fi.digitraffic.tis.vaco.http.model.ImmutableDownloadResponse;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableValidationInput;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import fi.digitraffic.tis.vaco.rules.model.ValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.Message;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class RulesetSubmissionServiceIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    private EntryService entryService;

    @Autowired
    private RulesetSubmissionService rulesetSubmissionService;

    @Autowired
    private MessagingService messagingService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskService taskService;

    @MockBean
    private VacoHttpClient httpClient;

    @Captor
    private ArgumentCaptor<Path> filePath;

    @Captor
    private ArgumentCaptor<String> entryUrl;

    @Captor
    private ArgumentCaptor<String> entryEtag;

    @Autowired
    private DownloadRule downloadRule;

    Path response;

    @Autowired
    private RulesetService rulesetService;

    @BeforeAll
    static void beforeAll(@Autowired VacoProperties vacoProperties) {
        createQueue(MessageQueue.ERRORS.getQueueName());
        createQueue(MessageQueue.RULE_RESULTS_INGEST.getQueueName());
        createQueue(MessageQueue.RULE_PROCESSING.munge(RuleName.GTFS_CANONICAL));

        CreateBucketResponse r = createBucket(vacoProperties.s3ProcessingBucket());
    }

    @BeforeEach
    void setUp() throws URISyntaxException {
        response = Path.of(ClassLoader.getSystemResource("integration/validation/smallfile.txt").toURI());
    }

    private Entry createEntryForTesting() {
        return entryService.create(
            TestObjects.anEntry("gtfs")
                .addValidations(ImmutableValidationInput.of(RuleName.GTFS_CANONICAL))
                .build())
            .get();
    }

    @Test
    void delegatesRuleProcessingToRuleSpecificQueueBasedOnRuleName() {
        Entry entry = createEntryForTesting();
        when(httpClient.downloadFile(filePath.capture(), entryUrl.capture(), eq(entry)))
            .thenReturn(CompletableFuture.supplyAsync(() -> ImmutableDownloadResponse.builder().body(Optional.ofNullable(response)).build()));


        String testQueueName = createSqsQueue(MessageQueue.RULE_PROCESSING.munge(RuleName.GTFS_CANONICAL));
        ResultMessage downloadedFile = downloadRule.execute(entry).join();

        Task task = taskService.findTask(entry.publicId(), RuleName.GTFS_CANONICAL).get();
        rulesetSubmissionService.submitTask(
            entry,
            task,
            // DownloadRule produces just a single file so this is OK
            TestObjects.aRuleset()
                .identifyingName(RuleName.GTFS_CANONICAL)
                .description("running rule from tests")
                .category(Category.SPECIFIC)
                .format(TransitDataFormat.GTFS)
                .build());

        // read generated messages from queue
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
        assertThat(message.inputs(), equalTo("s3://digitraffic-tis-processing-itest/entries/" + entry.publicId() + "/tasks/" + RuleName.GTFS_CANONICAL + "/rules/" + RuleName.GTFS_CANONICAL + "/input"));
        assertThat(message.outputs(), equalTo("s3://digitraffic-tis-processing-itest/entries/" + entry.publicId() + "/tasks/" + RuleName.GTFS_CANONICAL + "/rules/" + RuleName.GTFS_CANONICAL + "/output"));
        assertThat(message.source(), equalTo(RuleName.GTFS_CANONICAL));
    }

    @Test
    void sendsMessageToJobQueueForTaskWithFailedDependencies() throws InterruptedException {
        Entry entry = createEntryForTesting();
        when(httpClient.downloadFile(filePath.capture(), entryUrl.capture(), eq(entry)))
            .thenReturn(CompletableFuture.supplyAsync(() -> ImmutableDownloadResponse.builder().body(Optional.empty()).result(DownloadResponse.Result.OK).build()));

        String testQueueName = createSqsQueue(MessageQueue.RULE_PROCESSING.munge(RuleName.GTFS_CANONICAL));
        ResultMessage downloadedFile = downloadRule.execute(entry).join();

        Task task = taskService.findTask(entry.publicId(), RuleName.GTFS_CANONICAL).get();
        rulesetSubmissionService.submitTask(
            entry,
            task,
            rulesetService.findByName(RuleName.GTFS_CANONICAL).get());

        List<Message> ruleMessages = messagingService.readMessages(testQueueName).toList();

        assertThat(ruleMessages.size(), equalTo(0));
        Thread.sleep(10);
        Entry completedEntry = entryService.findEntry(entry.publicId()).get();
        Task dlTask = completedEntry.tasks().get(0);
        Task gtfsTask = completedEntry.tasks().get(1);
        assertThat(dlTask.status(), equalTo(Status.CANCELLED));
        assertThat(gtfsTask.status(), equalTo(Status.CANCELLED));
        assertThat(completedEntry.status(), equalTo(Status.CANCELLED));
    }

    @NotNull
    private static String createSqsQueue(String queueName) {
        CreateQueueResponse r = sqsClient.createQueue(CreateQueueRequest.builder().queueName(queueName).build());
        return queueName;
    }

}
