package fi.digitraffic.tis.vaco.delegator;

import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.TestConstants;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.RetryStatistics;
import fi.digitraffic.tis.vaco.process.PhaseRepository;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class DelegationJobQueueSqsListenerTests {

    private DelegationJobQueueSqsListener listener;

    private ImmutableDelegationJobMessage jobMessage;

    @Mock
    private MessagingService messagingService;
    @Mock
    private PhaseRepository phaseRepository;
    @Mock
    private Acknowledgement acknowledgement;
    @Captor
    private ArgumentCaptor<ValidationJobMessage> validationJob;

    @BeforeEach
    void setUp() {
        listener = new DelegationJobQueueSqsListener(messagingService, phaseRepository);
        jobMessage = ImmutableDelegationJobMessage.builder()
            .entry(ImmutableEntry.of("floppy disk", "https://example.solita", TestConstants.FINTRAFFIC_BUSINESS_ID))
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(messagingService, phaseRepository);
    }

    @Test
    void runsValidationByDefault() {
        listener.listen(jobMessage, acknowledgement);

        verify(messagingService).updateJobProcessingStatus(eq(jobMessage), eq(ProcessingState.START));
        verify(messagingService).updateJobProcessingStatus(eq(jobMessage), eq(ProcessingState.UPDATE));

        // no phases returned...
        verify(phaseRepository).findPhases(jobMessage.entry());
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

        // no phases returned...
        verify(phaseRepository).findPhases(alreadyStarted.entry());
        /// ...so validation is run as default
        verify(messagingService).submitValidationJob(validationJob.capture());
    }
}
