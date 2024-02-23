package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.SqsListenerBase;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationJobMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ValidationQueueSqsListener extends SqsListenerBase<ImmutableValidationJobMessage> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final MessagingService messagingService;
    private final RulesetSubmissionService rulesetSubmissionService;
    private final EntryService entryService;

    public ValidationQueueSqsListener(MessagingService messagingService,
                                      RulesetSubmissionService rulesetSubmissionService,
                                      EntryService entryService) {
        super((message, stats) -> messagingService.submitValidationJob(message.withRetryStatistics(stats)));
        this.messagingService = messagingService;
        this.rulesetSubmissionService = rulesetSubmissionService;
        this.entryService = entryService;
    }

    @SqsListener(QueueNames.VACO_JOBS_VALIDATION)
    public void listen(ImmutableValidationJobMessage message, Acknowledgement acknowledgement) {
        handle(message, message.entry().publicId(), acknowledgement, (ignored) -> {});
    }

    @Override
    protected void runTask(ImmutableValidationJobMessage message) {
        rulesetSubmissionService.submit(message);

        logger.debug("Submission complete for {}, resubmitting to delegation", message.entry().publicId());

        ImmutableDelegationJobMessage job = ImmutableDelegationJobMessage.builder()
            // refresh entry to avoid repeating same message over and over and over...and over again
            .entry(entryService.reload(message.entry()))
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
        messagingService.submitProcessingJob(job);
    }
}
