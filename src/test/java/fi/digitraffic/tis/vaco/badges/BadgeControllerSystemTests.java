package fi.digitraffic.tis.vaco.badges;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.db.model.TaskRecord;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
import fi.digitraffic.tis.vaco.db.repositories.TaskRepository;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
import fi.digitraffic.tis.vaco.rules.internal.StopsAndQuaysRule;
import fi.digitraffic.tis.vaco.validation.model.ImmutableRulesetSubmissionConfiguration;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationJobMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.BDDMockito.given;
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

    @Mock
    private HttpServletResponse response;
    @Autowired
    private TaskRepository taskRepository;

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
        Task validationTask = ImmutableTask.of(RuleName.GTFS_CANONICAL, 100);
        Entry entry = entryBuilder.addTasks(validationTask).build();
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

        Thread.sleep(5000);

        ClassPathResource classPathResource = badgeController.entryBadge(entryRecord.get().publicId(), response);
        assertThat(classPathResource, notNullValue());
        verifyBadgeIs(Status.CANCELLED);

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
