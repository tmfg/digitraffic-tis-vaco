package fi.digitraffic.tis.vaco.conversion;

import fi.digitraffic.tis.vaco.conversion.model.ImmutableConversionJobMessage;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.SqsListenerBase;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.springframework.stereotype.Component;

@Component
public class ConversionQueueSqsListener extends SqsListenerBase<ImmutableConversionJobMessage> {

    private final MessagingService messagingService;

    private final ConversionService conversionService;

    public ConversionQueueSqsListener(MessagingService messagingService, ConversionService conversionService) {
        super((message, stats) -> messagingService.submitConversionJob(message.withRetryStatistics(stats)));
        this.messagingService = messagingService;
        this.conversionService = conversionService;
    }

    @SqsListener(QueueNames.VACO_JOBS_CONVERSION)
    public void listen(ImmutableConversionJobMessage message, Acknowledgement acknowledgement) {
        handle(message, message.entry().publicId(), acknowledgement, ignored -> {});
    }

    @Override
    protected void runTask(ImmutableConversionJobMessage message) {
        conversionService.convert(message);
        ImmutableDelegationJobMessage job = ImmutableDelegationJobMessage.builder()
            .entry(message.entry())
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
        messagingService.submitProcessingJob(job);
    }
}
