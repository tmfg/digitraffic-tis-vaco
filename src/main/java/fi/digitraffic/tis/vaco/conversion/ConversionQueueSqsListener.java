package fi.digitraffic.tis.vaco.conversion;

import fi.digitraffic.tis.vaco.conversion.model.ImmutableConversionJobMessage;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.SqsListenerBase;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.QueueNames;
import fi.digitraffic.tis.vaco.entries.EntryRepository;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ConversionQueueSqsListener extends SqsListenerBase<ImmutableConversionJobMessage> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final MessagingService messagingService;
    private final ConversionService conversionService;
    private final EntryRepository entryRepository;

    public ConversionQueueSqsListener(MessagingService messagingService,
                                      ConversionService conversionService,
                                      EntryRepository entryRepository) {
        super((message, stats) -> messagingService.submitConversionJob(message.withRetryStatistics(stats)));
        this.messagingService = messagingService;
        this.conversionService = conversionService;
        this.entryRepository = entryRepository;
    }

    @SqsListener(QueueNames.VACO_JOBS_CONVERSION)
    public void listen(ImmutableConversionJobMessage message, Acknowledgement acknowledgement) {
        handle(message, message.entry().publicId(), acknowledgement, ignored -> {});
    }

    @Override
    protected void runTask(ImmutableConversionJobMessage message) {
        conversionService.convert(message);

        logger.debug("Conversion complete for {}, resubmitting to delegation", message.entry().publicId());

        ImmutableDelegationJobMessage job = ImmutableDelegationJobMessage.builder()
            .entry(entryRepository.reload(message.entry()))
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
        messagingService.submitProcessingJob(job);
    }
}
