package fi.digitraffic.tis.vaco.delegator;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.RetryStatistics;
import fi.digitraffic.tis.vaco.process.TaskRepository;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.repository.QueueHandlerRepository;
import fi.digitraffic.tis.vaco.validation.model.ValidationJobMessage;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class DelegationJobQueueSqsListenerTests extends SpringBootIntegrationTestBase {

    private DelegationJobQueueSqsListener listener;

    private ImmutableDelegationJobMessage jobMessage;

    @Autowired
    private QueueHandlerRepository queueHandlerRepository;

    @Mock
    private MessagingService messagingService;
    @Autowired
    private TaskService taskService;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private Acknowledgement acknowledgement;
    @Captor
    private ArgumentCaptor<ValidationJobMessage> validationJob;

    @BeforeEach
    void setUp() {
        listener = new DelegationJobQueueSqsListener(messagingService, taskService);
        jobMessage = ImmutableDelegationJobMessage.builder()
            .entry(createQueueEntryForTesting())
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
    }

    private Entry createQueueEntryForTesting() {
        return queueHandlerRepository.create(TestObjects.anEntry("gtfs").build());
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(messagingService, taskRepository);
    }

    @Test
    void runsValidationByDefault() {
        listener.listen(jobMessage, acknowledgement);
        verify(messagingService).submitValidationJob(validationJob.capture());
    }

    @Test
    void marksJobAsCompleteIfRetriesAreExhausted() {
        RetryStatistics retries = jobMessage.retryStatistics();
        ImmutableDelegationJobMessage tooManyRetries = jobMessage.withRetryStatistics(
            ImmutableRetryStatistics
                .copyOf(retries)
                .withTryNumber(retries.maxRetries() + 1)); // technically this is run 6 of 5, so it just skips everything

        doAnswer(invocation -> null).when(messagingService).updateJobProcessingStatus(tooManyRetries, ProcessingState.COMPLETE);

        listener.listen(tooManyRetries, acknowledgement);

        verify(messagingService).updateJobProcessingStatus(tooManyRetries, ProcessingState.COMPLETE);
    }

    @Test
    void willNotStartAlreadyStartedProcessingJob() {
        ImmutableEntry startedEntry = ImmutableEntry.copyOf(jobMessage.entry())
            .withStarted(LocalDateTime.now());
        ImmutableDelegationJobMessage alreadyStarted = jobMessage.withEntry(startedEntry);
        listener.listen(alreadyStarted, acknowledgement);

        verify(messagingService).submitValidationJob(validationJob.capture());
    }
}
