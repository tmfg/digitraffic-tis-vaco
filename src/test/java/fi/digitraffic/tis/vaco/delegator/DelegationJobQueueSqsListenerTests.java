package fi.digitraffic.tis.vaco.delegator;

import fi.digitraffic.tis.vaco.TestConstants;
import fi.digitraffic.tis.vaco.VacoException;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.DelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.RetryStatistics;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableQueueEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ProcessingState;
import fi.digitraffic.tis.vaco.queuehandler.repository.QueueHandlerRepository;
import fi.digitraffic.tis.vaco.validation.model.ValidationJobMessage;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class DelegationJobQueueSqsListenerTests {

    private DelegationJobQueueSqsListener listener;

    private ImmutableDelegationJobMessage jobMessage;

    @Mock
    private MessagingService messagingService;
    @Mock
    private QueueHandlerRepository queueHandlerRepository;
    @Mock
    private Acknowledgement acknowledgement;
    @Captor
    private ArgumentCaptor<DelegationJobMessage> delegationJob;
    @Captor
    private ArgumentCaptor<ValidationJobMessage> validationJob;

    @BeforeEach
    void setUp() {
        listener = new DelegationJobQueueSqsListener(messagingService, queueHandlerRepository);
        jobMessage = ImmutableDelegationJobMessage.builder()
            .entry(ImmutableQueueEntry.of("floppy disk", "https://example.solita", TestConstants.FINTRAFFIC_BUSINESS_ID))
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(messagingService, queueHandlerRepository);
    }

    @Test
    void runsValidationByDefault() {
        listener.listenVacoJobs(jobMessage, acknowledgement);

        verify(messagingService).updateJobProcessingStatus(eq(jobMessage), eq(ProcessingState.START));
        verify(messagingService).updateJobProcessingStatus(eq(jobMessage), eq(ProcessingState.UPDATE));

        // no phases returned...
        verify(queueHandlerRepository).findPhases(jobMessage.entry());
        /// ...so validation is run as default
        verify(messagingService).submitValidationJob(validationJob.capture());
    }

    @Test
    void retriesIfProcessingThrowsException() {
        doAnswer(invocation -> null).when(messagingService).updateJobProcessingStatus(jobMessage, ProcessingState.START);
        doThrow(new FakeVacoExeption("kra-pow!"))
            .when(messagingService).updateJobProcessingStatus(jobMessage, ProcessingState.UPDATE);

        listener.listenVacoJobs(jobMessage, acknowledgement);

        // no phases returned...
        verify(queueHandlerRepository).findPhases(jobMessage.entry());

        // ...first call succeeds...
        verify(messagingService).updateJobProcessingStatus(eq(jobMessage), eq(ProcessingState.START));
        // ...but the second one doesn't...
        verify(messagingService).updateJobProcessingStatus(eq(jobMessage), eq(ProcessingState.UPDATE));

        // ...so job is requeued...
        verify(messagingService).submitProcessingJob(delegationJob.capture());
        /// ...with updated retry count
        assertThat(delegationJob.getValue().retryStatistics().tryNumber(), equalTo(2));
    }

    @Test
    void lastTryIsStillExecutedNormally() {
        RetryStatistics retries = jobMessage.retryStatistics();
        ImmutableDelegationJobMessage lastTry = jobMessage.withRetryStatistics(
            ImmutableRetryStatistics
                .copyOf(retries)
                .withTryNumber(retries.maxRetries()));

        listener.listenVacoJobs(lastTry, acknowledgement);

        verify(messagingService).updateJobProcessingStatus(eq(lastTry), eq(ProcessingState.START));
        verify(messagingService).updateJobProcessingStatus(eq(lastTry), eq(ProcessingState.UPDATE));

        // no phases returned...
        verify(queueHandlerRepository).findPhases(lastTry.entry());
        /// ...so validation is run as default
        verify(messagingService).submitValidationJob(validationJob.capture());
    }

    @Test
    void stopsRequeuingIfRetriesAreExhaustedAndMarksJobAsComplete() {
        RetryStatistics retries = jobMessage.retryStatistics();
        ImmutableDelegationJobMessage tooManyRetries = jobMessage.withRetryStatistics(
            ImmutableRetryStatistics
                .copyOf(retries)
                .withTryNumber(retries.maxRetries() + 1)); // technically this is run 6 of 5, so it just skips everything

        doAnswer(invocation -> null).when(messagingService).updateJobProcessingStatus(tooManyRetries, ProcessingState.COMPLETE);

        listener.listenVacoJobs(tooManyRetries, acknowledgement);

        verify(messagingService).updateJobProcessingStatus(eq(tooManyRetries), eq(ProcessingState.COMPLETE));
    }

    @Test
    void willNotStartAlreadyStartedProcessingJob() {
        ImmutableQueueEntry startedEntry = ImmutableQueueEntry.copyOf(jobMessage.entry())
            .withStarted(LocalDateTime.now());
        ImmutableDelegationJobMessage alreadyStarted = jobMessage.withEntry(startedEntry);
        listener.listenVacoJobs(alreadyStarted, acknowledgement);

        // only UPDATE, no START
        verify(messagingService).updateJobProcessingStatus(eq(alreadyStarted), eq(ProcessingState.UPDATE));

        // no phases returned...
        verify(queueHandlerRepository).findPhases(alreadyStarted.entry());
        /// ...so validation is run as default
        verify(messagingService).submitValidationJob(validationJob.capture());
    }

    private class FakeVacoExeption extends VacoException {
        public FakeVacoExeption(String message) {
            super(message);
        }
    }
}
