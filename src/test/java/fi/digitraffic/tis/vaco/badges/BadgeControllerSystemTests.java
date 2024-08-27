package fi.digitraffic.tis.vaco.badges;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.db.model.TaskRecord;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
import fi.digitraffic.tis.vaco.db.repositories.TaskRepository;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.JobMessage;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class BadgeControllerSystemTests extends SpringBootIntegrationTestBase {

    @Autowired
    private BadgeController badgeController;

    @Autowired
    private EntryRepository entryRepository;

    @Autowired
    private MessagingService messagingService;

    @Autowired
    private RecordMapper recordMapper;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TestQueueListener testQueueListener;

    @Mock
    private HttpServletResponse response;

    @BeforeAll
    static void beforeAll() {
        createQueue("vaco-errors");
        createQueue("rules-results");
        createQueue("rules-processing-gtfs-canonical");
        createQueue("DLQ-rules-processing");
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(response);
    }



    @Test
    void newEntry_statusIsReceived() {
        Entry entry = TestObjects.anEntry("gtfs").build();
        Optional<EntryRecord> entryRecord = entryRepository.create(Optional.empty(), entry);
        assertThat(entryRecord.isPresent(), equalTo(true));

        ClassPathResource classPathResource = badgeController.entryBadge(entryRecord.get().publicId(), response);
        assertThat(classPathResource, notNullValue());
        verifyBadgeIs(Status.RECEIVED);
    }

    @Test
    void newEntry_statusIsProcessing() throws InterruptedException {
        Entry entry = TestObjects.anEntry("gtfs").build();
        Optional<EntryRecord> entryRecord = entryRepository.create(Optional.empty(), entry);
        assertThat(entryRecord.isPresent(), equalTo(true));

        long until = System.currentTimeMillis() + 200;

        while (until < System.currentTimeMillis()) {
            if (assertBadgeIs(Status.PROCESSING, entryRecord)) {
                break;
            }
        }
    }

    @Test
    void newEntry_statusIsCancelled() throws InterruptedException {
        ImmutableEntry.Builder entryBuilder = TestObjects.anEntry("gtfs");
        Task downloadTask = ImmutableTask.of(DownloadRule.PREPARE_DOWNLOAD_TASK, 100);
        Task validationTask = ImmutableTask.of(RuleName.GTFS_CANONICAL, 200);
        Entry entry = entryBuilder.addTasks(downloadTask, validationTask).build();
        assertThat(entry.tasks(), not(empty()));
        Optional<EntryRecord> entryRecord = entryRepository.create(Optional.empty(), entry);
        assertThat(entryRecord.isPresent(), equalTo(true));
        List<Task> tasks1 = entry.tasks();
        assertThat(tasks1, not(empty()));
        List<TaskRecord> tasks = taskRepository.createTasks(entryRecord.get(), tasks1);
        assertThat(tasks, not(empty()));

        Entry createdEntry = recordMapper.toEntryBuilder(entryRecord.get(),Optional.empty())
            .tasks(Streams.collect(tasks, recordMapper::toTask))
            .build();
        ImmutableDelegationJobMessage job = ImmutableDelegationJobMessage.builder()
            .entry(createdEntry)
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
        messagingService.submitProcessingJob(job);

        waitForMessagesInQueue(MessageQueue.RULE_PROCESSING.munge(RuleName.GTFS_CANONICAL), 1, 5_000);
        waitForEntryProcessingToFinish(createdEntry.publicId(), 5_000);

        ClassPathResource entryBadge = badgeController.entryBadge(entryRecord.get().publicId(), response);
        assertThat(entryBadge, notNullValue());
        verifyBadgeIs(Status.FAILED);

        ClassPathResource taskBadge = badgeController.taskBadge(entryRecord.get().publicId(), RuleName.GTFS_CANONICAL, response);
        assertThat(taskBadge, notNullValue());
        verifyBadgeIs(Status.CANCELLED);

    }

    private void waitForEntryProcessingToFinish(String publicId, int maxWait) throws InterruptedException {
        long until = System.currentTimeMillis() + maxWait;
        while (entryRepository.findByPublicId(publicId).map(EntryRecord::completed).isEmpty()
            && (System.currentTimeMillis() < until)) {
            Thread.sleep(100);
        }
    }

    private List<JobMessage> waitForMessagesInQueue(String queue, int count, int maxWait) throws InterruptedException {
        long until = System.currentTimeMillis() + maxWait;
        while (messagesAvailable(queue) <= count
               && (System.currentTimeMillis() < until)) {
            Thread.sleep(100);
        }
        return readMessages(queue);
    }

    private int messagesAvailable(String queue) {
        return readMessages(queue).size();
    }

    private List<JobMessage> readMessages(String queue) {
        return testQueueListener.getProcessingMessages().getOrDefault(queue, List.of());
    }

    boolean assertBadgeIs(Status status, Optional<EntryRecord> entryRecord) throws InterruptedException {
        ClassPathResource cpr = badgeController.entryBadge(entryRecord.get().publicId(), response);

        if (cpr.exists()) {
            verifyBadgeIs(status);
            return true;
        } else {
            Thread.sleep(10);
            return false;
        }
    }

    private void verifyBadgeIs(Status status) {
        String expectedName = status.fieldName();
        verify(response, times(1)).addHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + expectedName + ".svg\"");
    }
}
