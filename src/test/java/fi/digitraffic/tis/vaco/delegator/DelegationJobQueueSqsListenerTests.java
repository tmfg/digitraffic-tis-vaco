package fi.digitraffic.tis.vaco.delegator;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.conversion.ConversionService;
import fi.digitraffic.tis.vaco.delegator.model.TaskCategory;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.RetryStatistics;
import fi.digitraffic.tis.vaco.process.TaskRepository;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.repository.QueueHandlerRepository;
import fi.digitraffic.tis.vaco.validation.ValidationService;
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
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private ValidationService validationService;
    @Mock
    private ConversionService conversionService;
    @Mock
    private Acknowledgement acknowledgement;
    @Captor
    private ArgumentCaptor<ValidationJobMessage> validationJob;

    @BeforeEach
    void setUp() {
        listener = new DelegationJobQueueSqsListener(messagingService, taskRepository, validationService, conversionService);
        jobMessage = ImmutableDelegationJobMessage.builder()
            .entry(createQueueEntryForTesting())
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
    }

    private ImmutableEntry createQueueEntryForTesting() {
        return queueHandlerRepository.create(TestObjects.anEntry("gtfs").build());
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(messagingService, taskRepository, validationService, conversionService);
    }

    @Test
    void runsValidationByDefault() {
        listener.listen(jobMessage, acknowledgement);

        verify(messagingService).updateJobProcessingStatus(eq(jobMessage), eq(ProcessingState.START));
        verify(messagingService).updateJobProcessingStatus(eq(jobMessage), eq(ProcessingState.UPDATE));

        // no tasks returned...
        verify(taskRepository).findTasks(jobMessage.entry().id());
        /// ...so validation is run as default
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

        verify(messagingService).updateJobProcessingStatus(eq(tooManyRetries), eq(ProcessingState.COMPLETE));
    }

    @Test
    void willNotStartAlreadyStartedProcessingJob() {
        ImmutableEntry startedEntry = ImmutableEntry.copyOf(jobMessage.entry())
            .withStarted(LocalDateTime.now());
        ImmutableDelegationJobMessage alreadyStarted = jobMessage.withEntry(startedEntry);
        listener.listen(alreadyStarted, acknowledgement);

        // only UPDATE, no START
        verify(messagingService).updateJobProcessingStatus(eq(alreadyStarted), eq(ProcessingState.UPDATE));

        // no tasks returned...
        verify(taskRepository).findTasks(alreadyStarted.entry().id());
        /// ...so validation is run as default
        verify(messagingService).submitValidationJob(validationJob.capture());
    }

    @Test
    void canDetectAllKnownTaskCategoriesFromTasks() {
        Arrays.stream(TaskCategory.values())
            .forEach(tc ->
                assertThat(tc,
                    equalTo(DelegationJobQueueSqsListener.asTaskCategory(
                        ImmutableTask.of(1L, tc.name().toLowerCase() + ".testing", tc.priority)))));
    }
}
