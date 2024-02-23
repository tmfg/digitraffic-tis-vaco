package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.vaco.TestConstants;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.DelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.RetryStatistics;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import fi.digitraffic.tis.vaco.validation.model.ImmutableRulesetSubmissionConfiguration;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidationQueueSqsListenerTests {

    private ValidationQueueSqsListener listener;

    private ImmutableValidationJobMessage message;

    private ImmutableEntry entry;

    @Mock
    private Acknowledgement acknowledgement;
    @Mock
    private MessagingService messagingService;
    @Mock
    private RulesetSubmissionService rulesetSubmissionService;
    @Mock
    private EntryService entryService;
    @Captor
    private ArgumentCaptor<DelegationJobMessage> delegationJobMessage;

    @BeforeEach
    void setUp() {
        listener = new ValidationQueueSqsListener(messagingService, rulesetSubmissionService, entryService);

        entry = ImmutableEntry.of("entry", TestConstants.FORMAT_GTFS, TestConstants.EXAMPLE_URL, Constants.FINTRAFFIC_BUSINESS_ID);
        RetryStatistics retryStatistics = ImmutableRetryStatistics.of(1);
        message = ImmutableValidationJobMessage.builder()
            .entry(entry)
            .retryStatistics(retryStatistics)
            .configuration(ImmutableRulesetSubmissionConfiguration.of(RulesetSubmissionService.VALIDATE_TASK, Type.VALIDATION_SYNTAX))
            .build();
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(acknowledgement, messagingService, rulesetSubmissionService);
    }

    @Test
    void submitsItselfBackToDelegationQueueAndAcknowledgesOriginalMessageOnCompletion() {
        when(entryService.reload(entry)).thenReturn(entry);

        listener.listen(message, acknowledgement);

        verify(rulesetSubmissionService).submit(message);
        verify(messagingService).submitProcessingJob(delegationJobMessage.capture());
        verify(acknowledgement).acknowledge();
    }

}
