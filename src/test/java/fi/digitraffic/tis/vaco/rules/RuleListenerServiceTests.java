package fi.digitraffic.tis.vaco.rules;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.findings.FindingService;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.DelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.packages.model.ImmutablePackage;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.rules.model.ImmutableResultMessage;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class RuleListenerServiceTests {

    private RuleListenerService ruleListenerService;

    private ObjectMapper objectMapper;
    private VacoProperties vacoProperties;
    @Mock private MessagingService messagingService;
    @Mock private FindingService findingService;
    @Mock private S3Client s3Client;
    @Mock private QueueHandlerService queueHandlerService;
    @Mock private PackagesService packagesService;
    @Mock private TaskService taskService;
    @Mock private RulesetService rulesetService;
    @Mock private GtfsTaskSummaryService gtfsTaskSummaryService;
    @Captor private ArgumentCaptor<DelegationJobMessage> submittedProcessingJob;

    /**
     * This is ignored because in mocking the files residing in S3 are not available for filtering.
     */
    private final static String IGNORED_PATH_VALUE = "IGNORED ON PURPOSE. If you see this in output, something is broken.";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModules(new GuavaModule());
        vacoProperties = TestObjects.vacoProperties();
        ruleListenerService = new RuleListenerService(
            messagingService,
            findingService,
            objectMapper,
            s3Client,
            vacoProperties,
            queueHandlerService,
            packagesService,
            taskService,
            rulesetService);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(
            messagingService,
            findingService,
            s3Client,
            queueHandlerService,
            packagesService,
            taskService,
            rulesetService);
    }

    @Test
    void canHandleResultOfGtfs2NetexConversion() throws JsonProcessingException {
        Entry entry = entryWithTask(e -> ImmutableTask.of(e.id(), RuleName.GTFS2NETEX_FINTRAFFIC_1_0_0, 100).withId(9_000_000L));
        Task conversionTask = entry.tasks().get(0);
        Map<String, List<String>> uploadedFiles = Map.of(
            "file.txt", List.of("all", "debug"),
            "another.txt", List.of("debug"));
        Message gtfs2netexMessage = sqsMessage(asResultMessage(entry, conversionTask, uploadedFiles));

        givenResultPrerequisitesAreMet(entry, conversionTask);
        given(messagingService.readMessages(QueueNames.VACO_RULES_RESULTS)).willReturn(Stream.of(gtfs2netexMessage));
        givenGetEntry(entry).willReturn(entry);
        givenPackageIsCreated("all", entry, conversionTask).willReturn(ImmutablePackage.of(conversionTask.id(), "all", IGNORED_PATH_VALUE));
        givenPackageIsCreated("debug", entry, conversionTask).willReturn(ImmutablePackage.of(conversionTask.id(), "debug", IGNORED_PATH_VALUE));
        givenResultProcessingResultsInNewProcessingJobSubmission();

        ruleListenerService.handleRuleResultsIngestQueue();

        then(messagingService).should().deleteMessage(QueueNames.VACO_RULES_RESULTS, gtfs2netexMessage);
        assertThat(submittedProcessingJob.getValue().entry(), equalTo(entry));
    }

    private BDDMockito.BDDMyOngoingStubbing<Package> givenPackageIsCreated(String packageName, Entry entry, Task task) {
        return given(packagesService.createPackage(
            eq(entry),
            eq(task),
            eq(packageName),
            eq(S3Path.of("outputs")),
            eq(packageName + ".zip"),
            any()));
    }

    /**
     * Entry and Task exists, state change is tracked.
     */
    private void givenResultPrerequisitesAreMet(Entry entry, Task task) {
        givenFindEntry(entry).willReturn(Optional.of(entry));
        givenFindTask(entry).willReturn(Optional.of(task));
        givenTaskProgressIsTracked();
        givenTaskStatusIsTracked();
    }

    @NotNull
    private ResultMessage asResultMessage(Entry entry, Task conversionTask, Map<String, ? extends List<String>> uploadedFiles) {
        return ImmutableResultMessage.builder()
            .ruleName(RuleName.GTFS2NETEX_FINTRAFFIC_1_0_0)
            .entryId(entry.publicId())
            .taskId(conversionTask.id())
            .inputs("s3://" + vacoProperties.s3ProcessingBucket() + "/inputs")
            .outputs("s3://" + vacoProperties.s3ProcessingBucket() + "/outputs")
            .uploadedFiles(uploadedFiles)
            .build();
    }

    @NotNull
    private BDDMockito.BDDMyOngoingStubbing<Entry> givenGetEntry(Entry entry) {
        return given(queueHandlerService.getEntry(entry.publicId(), true));
    }

    @NotNull
    private BDDMockito.BDDMyOngoingStubbing<Optional<Entry>> givenFindEntry(Entry entry) {
        return given(queueHandlerService.findEntry(entry.publicId()));
    }

    private BDDMockito.BDDMyOngoingStubbing<Optional<Task>> givenFindTask(Entry entry) {
        return given(taskService.findTask(entry.id(), RuleName.GTFS2NETEX_FINTRAFFIC_1_0_0));
    }

    /**
     * New job is submitted if everything went OK
     */
    private void givenResultProcessingResultsInNewProcessingJobSubmission() {
        given(messagingService.submitProcessingJob(submittedProcessingJob.capture()))
            .will(a -> CompletableFuture.completedFuture(a.getArgument(0)));
    }

    private void givenTaskProgressIsTracked() {
        given(taskService.trackTask(any(), any())).will(a -> a.getArgument(0));
    }

    private void givenTaskStatusIsTracked() {
        given(taskService.markStatus(any(), any())).will(a -> a.getArgument(0));
    }

    private Entry entryWithTask(Function<Entry, Task> taskCreator) {
        ImmutableEntry.Builder entryBuilder = TestObjects.anEntry("gtfs");
        ImmutableEntry entry = entryBuilder.build();
        entry = entry.withTasks(taskCreator.apply(entry));
        return entry;
    }

    private Message sqsMessage(ResultMessage message) throws JsonProcessingException {
        return Message.builder()
            .body(objectMapper.writeValueAsString(message))
            .build();
    }
}
