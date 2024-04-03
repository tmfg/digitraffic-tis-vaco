package fi.digitraffic.tis.vaco.delegator;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.email.EmailService;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.RetryStatistics;
import fi.digitraffic.tis.vaco.process.TaskRepository;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
import fi.digitraffic.tis.vaco.rules.internal.StopsAndQuaysRule;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.validation.RulesetSubmissionService;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class DelegationJobQueueSqsListenerTests extends SpringBootIntegrationTestBase {

    private DelegationJobQueueSqsListener listener;

    private ImmutableDelegationJobMessage jobMessage;

    private Entry entry;

    @Autowired
    RecordMapper recordMapper;

    @Autowired
    private TaskService taskService;
    @Autowired
    private RulesetService rulesetService;
    @Autowired
    private EmailService emailService;
    @Autowired
    private EntryService entryService;

    @Mock
    private Acknowledgement acknowledgement;

    @Mock
    private MessagingService messagingService;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private DownloadRule downloadRule;
    @Mock
    private StopsAndQuaysRule stopsAndQuaysRule;
    @Mock
    private RulesetSubmissionService rulesetSubmissionService;

    @BeforeEach
    void setUp() {
        listener = new DelegationJobQueueSqsListener(
            messagingService,
            taskService,
            rulesetService,
            downloadRule,
            stopsAndQuaysRule,
            emailService,
            entryService,
            rulesetSubmissionService);
        entry = createQueueEntryForTesting();
        jobMessage = ImmutableDelegationJobMessage.builder()
            .entry(entry)
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
    }

    private Entry createQueueEntryForTesting() {
        return entryService.create(TestObjects.anEntry("gtfs").build()).get();
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(messagingService, taskRepository, downloadRule, stopsAndQuaysRule, rulesetSubmissionService);
    }

    @Test
    void marksJobAsCompleteIfRetriesAreExhausted() {
        RetryStatistics retries = jobMessage.retryStatistics();
        ImmutableDelegationJobMessage tooManyRetries = jobMessage.withRetryStatistics(
            ImmutableRetryStatistics
                .copyOf(retries)
                .withTryNumber(retries.maxRetries() + 1)); // technically this is run 6 of 5, so it just skips everything

        listener.listen(tooManyRetries, acknowledgement);

        Entry result = entryService.findEntry(entry.publicId()).get();

        assertThat(result.completed(), notNullValue());
    }
}
