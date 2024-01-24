package fi.digitraffic.tis.vaco.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.http.HttpClient;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableValidationInput;
import fi.digitraffic.tis.vaco.entries.EntryRepository;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import fi.digitraffic.tis.vaco.rules.model.ValidationRuleJobMessage;
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

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

class RulesetSubmissionServiceIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    private EntryRepository entryRepository;

    @Autowired
    private RulesetSubmissionService rulesetSubmissionService;

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

    @Autowired
    private DownloadRule downloadRule;

    Path response;

    @BeforeAll
    static void beforeAll(@Autowired VacoProperties vacoProperties) {
        CreateBucketResponse r = createBucket(vacoProperties.s3ProcessingBucket());
    }

    @BeforeEach
    void setUp() throws URISyntaxException {
        response = Path.of(ClassLoader.getSystemResource("integration/validation/smallfile.txt").toURI());
    }

    private Entry createEntryForTesting() {
        return entryRepository.create(TestObjects.anEntry("gtfs").addValidations(ImmutableValidationInput.of(RuleName.GTFS_CANONICAL_4_0_0)).build());
    }

    @Test
    void delegatesRuleProcessingToRuleSpecificQueueBasedOnRuleName() {
        when(httpClientUtility.downloadFile(filePath.capture(), entryUrl.capture(), entryEtag.capture()))
            .thenReturn(CompletableFuture.supplyAsync(() -> Optional.ofNullable(response)));

        Entry entry = createEntryForTesting();
        String testQueueName = createSqsQueue();
        ResultMessage downloadedFile = downloadRule.execute(entry).join();

        Task task = taskService.findTask(entry.id(), RulesetSubmissionService.VALIDATE_TASK).get();
        rulesetSubmissionService.submitRules(
            entry,
            task,
            // DownloadRule produces just a single file so this is OK
            Set.of(TestObjects.aRuleset()
                .identifyingName(RuleName.GTFS_CANONICAL_4_0_0)
                .description("running rule from tests")
                .category(Category.SPECIFIC)
                .format(TransitDataFormat.GTFS)
                .build()));

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
        assertThat(message.inputs(), equalTo("s3://digitraffic-tis-processing-itest/entries/" + entry.publicId() + "/tasks/" + RuleName.GTFS_CANONICAL_4_0_0 + "/rules/" + RuleName.GTFS_CANONICAL_4_0_0 + "/input"));
        assertThat(message.outputs(), equalTo("s3://digitraffic-tis-processing-itest/entries/" + entry.publicId() + "/tasks/" + RuleName.GTFS_CANONICAL_4_0_0 + "/rules/" + RuleName.GTFS_CANONICAL_4_0_0 + "/output"));
        assertThat(message.source(), equalTo(RulesetSubmissionService.VALIDATE_TASK));
    }

    @NotNull
    private static String createSqsQueue() {
        String testQueueName = MessageQueue.RULE_PROCESSING.munge(RuleName.GTFS_CANONICAL_4_0_0);
        CreateQueueResponse r = sqsClient.createQueue(CreateQueueRequest.builder().queueName(testQueueName).build());
        return testQueueName;
    }

}
