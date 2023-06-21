package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.SqsListenerBase;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
import fi.digitraffic.tis.vaco.validation.model.ImmutableJobMessage;
import fi.digitraffic.tis.vaco.validation.model.JobResult;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.springframework.stereotype.Component;

@Component
public class ValidationQueueSqsListener extends SqsListenerBase<ImmutableJobMessage> {

    private final MessagingService messagingService;

    private final ValidationService validationService;

    public ValidationQueueSqsListener(MessagingService messagingService,
                                      ValidationService validationService) {
        super((message, stats) -> messagingService.submitValidationJob(message.withRetryStatistics(stats)));
        this.messagingService = messagingService;
        this.validationService = validationService;
    }

    @SqsListener(QueueNames.VACO_JOBS_VALIDATION)
    public void listen(ImmutableJobMessage message, Acknowledgement acknowledgement) {
        handle(message, message.message().publicId(), acknowledgement, (ignored) -> {});
    }

    @Override
    protected void runTask(ImmutableJobMessage message) {
        // TODO: We have full results available here, but don't do anything with them - maybe we don't need 'em?
        JobResult result = validationService.validate(message);

        ImmutableDelegationJobMessage job = ImmutableDelegationJobMessage.builder()
            .entry(message.message())
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
        messagingService.submitProcessingJob(job);
    }
}
