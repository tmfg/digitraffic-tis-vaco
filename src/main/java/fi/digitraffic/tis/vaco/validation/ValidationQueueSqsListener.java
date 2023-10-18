package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.SqsListenerBase;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
import fi.digitraffic.tis.vaco.queuehandler.repository.QueueHandlerRepository;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationJobMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.springframework.stereotype.Component;

@Component
public class ValidationQueueSqsListener extends SqsListenerBase<ImmutableValidationJobMessage> {

    private final MessagingService messagingService;

    private final ValidationService validationService;
    private final QueueHandlerRepository queueHandlerRepository;

    public ValidationQueueSqsListener(MessagingService messagingService,
                                      ValidationService validationService,
                                      QueueHandlerRepository queueHandlerRepository) {
        super((message, stats) -> messagingService.submitValidationJob(message.withRetryStatistics(stats)));
        this.messagingService = messagingService;
        this.validationService = validationService;
        this.queueHandlerRepository = queueHandlerRepository;
    }

    @SqsListener(QueueNames.VACO_JOBS_VALIDATION)
    public void listen(ImmutableValidationJobMessage message, Acknowledgement acknowledgement) {
        handle(message, message.entry().publicId(), acknowledgement, (ignored) -> {});
    }

    @Override
    protected void runTask(ImmutableValidationJobMessage message) {
        validationService.validate(message);

        ImmutableDelegationJobMessage job = ImmutableDelegationJobMessage.builder()
            // refresh entry to avoid repeating same message over and over and over...and over again
            .entry(queueHandlerRepository.findByPublicId(message.entry().publicId()).get())
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
        messagingService.submitProcessingJob(job);
    }
}
