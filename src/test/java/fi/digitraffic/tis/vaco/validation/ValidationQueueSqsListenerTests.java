package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.vaco.TestConstants;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.DelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.RetryStatistics;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationJobMessage;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ValidationQueueSqsListenerTests {

    private ValidationQueueSqsListener listener;

    private ImmutableValidationJobMessage message;

    private Entry entry;

    @Mock
    private Acknowledgement acknowledgement;
    @Mock
    private MessagingService messagingService;
    @Mock
    private ValidationService validationService;
    @Captor
    private ArgumentCaptor<DelegationJobMessage> delegationJobMessage;

    @BeforeEach
    void setUp() {
        listener = new ValidationQueueSqsListener(messagingService, validationService);

        entry = ImmutableEntry.of(TestConstants.FORMAT_GTFS, TestConstants.EXAMPLE_URL, TestConstants.FINTRAFFIC_BUSINESS_ID);
        RetryStatistics retryStatistics = ImmutableRetryStatistics.of(1);
        message = ImmutableValidationJobMessage.builder().message(entry).retryStatistics(retryStatistics).build();
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(acknowledgement, messagingService, validationService);
    }

    @Test
    void submitsItselfBackToDelegationQueueAndAcknowledgesOriginalMessageOnCompletion() {
        listener.listen(message, acknowledgement);

        verify(validationService).validate(message);
        verify(messagingService).submitProcessingJob(delegationJobMessage.capture());
        verify(acknowledgement).acknowledge();
    }
}