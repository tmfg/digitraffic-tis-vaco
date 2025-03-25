package fi.digitraffic.tis.vaco.rules;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.db.model.FindingRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableFindingRecord;
import fi.digitraffic.tis.vaco.db.repositories.FindingRepository;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.findings.FindingService;
import fi.digitraffic.tis.vaco.findings.model.Finding;
import fi.digitraffic.tis.vaco.findings.model.FindingSeverity;
import fi.digitraffic.tis.vaco.findings.model.ImmutableFinding;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.DelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import fi.digitraffic.tis.vaco.rules.results.GbfsEnturResultProcessor;
import fi.digitraffic.tis.vaco.rules.results.GtfsCanonicalResultProcessor;
import fi.digitraffic.tis.vaco.rules.results.GtfsToNetexResultProcessor;
import fi.digitraffic.tis.vaco.rules.results.InternalRuleResultProcessor;
import fi.digitraffic.tis.vaco.rules.results.NetexEnturValidatorResultProcessor;
import fi.digitraffic.tis.vaco.rules.results.NetexToGtfsRuleResultProcessor;
import fi.digitraffic.tis.vaco.rules.results.ResultProcessor;
import fi.digitraffic.tis.vaco.rules.results.SimpleResultProcessor;
import fi.digitraffic.tis.vaco.summary.SummaryService;
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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

import static fi.digitraffic.tis.vaco.rules.ResultProcessorTestHelpers.asResultMessage;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class RuleResultsListenerTests {

    private RuleResultsListener ruleResultsListener;

    private ObjectMapper objectMapper;
    private VacoProperties vacoProperties;
    @Mock private MessagingService messagingService;
    @Mock private FindingService findingService;
    @Mock private QueueHandlerService queueHandlerService;
    @Mock private EntryService entryService;
    @Mock private TaskService taskService;
    @Mock private NetexEnturValidatorResultProcessor netexEnturProcessor;
    @Mock private GbfsEnturResultProcessor gbfsResultProcessor;
    @Mock private GtfsCanonicalResultProcessor gtfsCanonicalProcessor;
    @Mock private SimpleResultProcessor simpleResultProcessor;
    @Mock private InternalRuleResultProcessor internalRuleResultProcessor;
    @Mock private GtfsToNetexResultProcessor gtfsToNetexResultProcessor;
    @Mock private NetexToGtfsRuleResultProcessor netexToGtfsRuleResultProcessor;
    @Mock private SummaryService summaryService;
    @Captor private ArgumentCaptor<DelegationJobMessage> submittedProcessingJob;
    @Mock private FindingRepository findingRepository;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new GuavaModule());
        vacoProperties = TestObjects.vacoProperties();
        ruleResultsListener = new RuleResultsListener(
            messagingService,
            findingService,
            objectMapper,
            queueHandlerService,
            taskService,
            entryService,
            netexEnturProcessor,
            gbfsResultProcessor,
            gtfsCanonicalProcessor,
            simpleResultProcessor,
            internalRuleResultProcessor,
            summaryService,
            gtfsToNetexResultProcessor,
            netexToGtfsRuleResultProcessor,
            findingRepository);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(
            messagingService,
            findingService,
            queueHandlerService,
            entryService,
            taskService,
            netexEnturProcessor,
            gbfsResultProcessor,
            gtfsCanonicalProcessor,
            simpleResultProcessor,
            internalRuleResultProcessor,
            gtfsToNetexResultProcessor,
            netexToGtfsRuleResultProcessor,
            findingRepository);
    }

    @Test
    void canonicalGtfsValidator410UsesCanonicalResultProcessor() throws JsonProcessingException {
        assertResultProcessorIsUsed(RuleName.GTFS_CANONICAL, gtfsCanonicalProcessor);
    }

    @Test
    void netexEnturValidatorUsesEnturResultProcessor() throws JsonProcessingException {
        assertResultProcessorIsUsed(RuleName.NETEX_ENTUR, netexEnturProcessor);
    }

    @Test
    void fintrafficGtfs2NetexConversionUsesSimpleResultProcessor() throws JsonProcessingException {
        assertResultProcessorIsUsed(RuleName.GTFS2NETEX_FINTRAFFIC, gtfsToNetexResultProcessor);
    }

    @Test
    void enturNetex2GtfsConversionUsesSimpleResultProcessor() throws JsonProcessingException {
        assertResultProcessorIsUsed(RuleName.NETEX2GTFS_ENTUR, netexToGtfsRuleResultProcessor);
    }

    @Test
    void deadLetterQueueTest() throws JsonProcessingException {

        String jsonString = """
                {
                  "entry": { "publicId":"abc1"},
                  "task": { "id":2, "publicId":"abc2", "name": "gtfs.canonical" }
                }
            """;

        JsonNode message = objectMapper.readValue(jsonString, JsonNode.class);
        Task task = ImmutableTask.of("gtfs.canonical", 100).withPublicId("abc2");
        given(taskService.markStatus(task, Status.FAILED)).willReturn(task);
        given(taskService.findTask(task.publicId())).willReturn(Optional.of(task));

        ImmutableEntry.Builder entryBuilder = TestObjects.anEntry("gtfs");
        Entry entry = entryBuilder.addTasks(task)
            .publicId("abc1")
            .name("dlq test entry")
            .format("gtfs")
            .url("http://example.fintraffic")
            .businessId(Constants.FINTRAFFIC_BUSINESS_ID)
            .sendNotifications(false)
            .build();

        given(entryService.findEntry(entry.publicId())).willReturn(Optional.of(entry));
        givenResultProcessingResultsInNewProcessingJobSubmission();

        Finding finding = ImmutableFinding.builder()
            .publicId(entry.publicId())
            .source(task.name())
            .taskId(2L)
            .message("Entry ended up in dead letter queue ")
            .severity(FindingSeverity.ERROR)
            .build();

        FindingRecord findingRecord = ImmutableFindingRecord.builder()
            .id(500L)
            .publicId(NanoIdUtils.randomNanoId())
            .taskId(500L)
            .source(task.name())
            .build();

        given(findingRepository.create(finding)).willReturn(findingRecord);
        given(taskService.trackTask(entry, task, ProcessingState.COMPLETE)).willReturn(task);
        given(taskService.cancelRemainingTasks(entry)).willReturn(true);
        assertThat(ruleResultsListener.handleDeadLetter(message).join(), equalTo(true));
    }

    @Test
    void deadLetterQueueWithoutTaskTest() throws JsonProcessingException {

        String jsonString = """
                {
                  "entry": { "publicId":"abc1" }
                }
            """;

        JsonNode message = objectMapper.readValue(jsonString, JsonNode.class);

        Entry entry = ImmutableEntry.of("abc1", "dlq test entry", "gtfs", "http://example.fintraffic", Constants.FINTRAFFIC_BUSINESS_ID, false);
        given(entryService.findEntry(entry.publicId())).willReturn(Optional.of(entry));
        givenResultProcessingResultsInNewProcessingJobSubmission();

        assertThat(ruleResultsListener.handleDeadLetter(message).join(), equalTo(false));
    }

    private void assertResultProcessorIsUsed(String ruleName, ResultProcessor resultProcessor) throws JsonProcessingException {
        Entry entry = entryForRule(ruleName);
        ResultMessage resultMessage = asResultMessage(vacoProperties, ruleName, entry, Map.of());
        Message gtfs2netexMessage = sqsMessage(objectMapper, resultMessage);

        givenMatchingResultProcessorIsUsed(ruleName, entry, resultProcessor, gtfs2netexMessage);
        ruleResultsListener.handleRuleResultsIngestQueue();
        then(messagingService).should().deleteMessage(QueueNames.VACO_RULES_RESULTS, gtfs2netexMessage);
        assertThat(submittedProcessingJob.getValue().entry(), equalTo(entry));
    }

    private void givenMatchingResultProcessorIsUsed(String ruleName, Entry entry, ResultProcessor resultProcessor, Message message) {
        givenResultPrerequisitesAreMet(ruleName, entry);
        givenMessageIsInQueue(QueueNames.VACO_RULES_RESULTS, message);
        givenResultProcessorCompletesWith(resultProcessor, true);
        givenGetEntry(entry).willReturn(entry);
        givenResultProcessingResultsInNewProcessingJobSubmission();
    }

    @NotNull
    private static Entry entryForRule(String ruleName) {
        return entryWithTask(e -> ImmutableTask.of(ruleName, 100).withId(9_000_000L));
    }

    private void givenMessageIsInQueue(String queueName, Message gtfs2netexMessage) {
        given(messagingService.readMessages(queueName)).willReturn(Stream.of(gtfs2netexMessage));
    }

    /**
     * Entry and Task exists, state change is tracked.
     */
    private void givenResultPrerequisitesAreMet(String ruleName, Entry entry) {
        givenFindEntry(entry).willReturn(Optional.of(entry));
        givenFindTask(ruleName, entry).willReturn(Optional.of(entry.tasks().get(0)));
        givenTaskProgressIsTracked();
        givenTaskStatusIsTracked();
    }

    private BDDMockito.BDDMyOngoingStubbing<Boolean> givenResultProcessorCompletesWith(ResultProcessor resultProcessor, boolean result) {
        return given(resultProcessor.processResults(any(), any(), any())).willReturn(result);
    }

    @NotNull
    private BDDMockito.BDDMyOngoingStubbing<Entry> givenGetEntry(Entry entry) {
        return given(queueHandlerService.getEntry(entry.publicId()));
    }

    @NotNull
    private BDDMockito.BDDMyOngoingStubbing<Optional<Entry>> givenFindEntry(Entry entry) {
        return given(entryService.findEntry(entry.publicId()));
    }

    private BDDMockito.BDDMyOngoingStubbing<Optional<Task>> givenFindTask(String ruleName, Entry entry) {
        return given(taskService.findTask(entry.publicId(), ruleName));
    }

    /**
     * New job is submitted if everything went OK
     */
    private void givenResultProcessingResultsInNewProcessingJobSubmission() {
        given(messagingService.submitProcessingJob(submittedProcessingJob.capture()))
            .will(a -> CompletableFuture.completedFuture(a.getArgument(0)));
    }

    private void givenTaskProgressIsTracked() {
        given(taskService.trackTask(any(), any(), any())).will(a -> a.getArgument(1));
    }

    private void givenTaskStatusIsTracked() {
        given(taskService.markStatus(any(), any(), any())).will(a -> a.getArgument(1));
    }

    private static Entry entryWithTask(Function<Entry, Task> taskCreator) {
        ImmutableEntry.Builder entryBuilder = TestObjects.anEntry("gtfs");
        ImmutableEntry entry = entryBuilder.build();
        entry = entry.withTasks(taskCreator.apply(entry));
        return entry;
    }

    private static Message sqsMessage(ObjectMapper objectMapper, ResultMessage message) throws JsonProcessingException {
        return Message.builder()
            .body(objectMapper.writeValueAsString(message))
            .build();
    }
}
