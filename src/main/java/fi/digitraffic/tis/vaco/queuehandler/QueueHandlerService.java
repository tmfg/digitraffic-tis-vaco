package fi.digitraffic.tis.vaco.queuehandler;

import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.queuehandler.dto.ImmutableEntryRequest;
import fi.digitraffic.tis.vaco.queuehandler.mapper.EntryRequestMapper;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.repository.QueueHandlerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class QueueHandlerService {

    private final MessagingService messagingService;

    private final QueueHandlerRepository queueHandlerRepository;

    private final EntryRequestMapper entryRequestMapper;



    public QueueHandlerService(EntryRequestMapper entryRequestMapper,
                               MessagingService messagingService,
                               QueueHandlerRepository queueHandlerRepository) {
        this.entryRequestMapper = entryRequestMapper;
        this.messagingService = messagingService;
        this.queueHandlerRepository = queueHandlerRepository;
    }

    @Transactional
    public ImmutableEntry processQueueEntry(ImmutableEntryRequest entryRequest) {
        Entry converted = entryRequestMapper.toEntry(entryRequest);
        ImmutableEntry result = queueHandlerRepository.create(converted);

        ImmutableDelegationJobMessage job = ImmutableDelegationJobMessage.builder()
            .entry(result)
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
        messagingService.submitProcessingJob(job);

        return result;
    }

    public Optional<ImmutableEntry> getQueueEntryView(String publicId) {
        return queueHandlerRepository.findByPublicId(publicId);
    }

    public List<ImmutableEntry> getAllQueueEntriesFor(String businessId, boolean full) {
        return queueHandlerRepository.findAllByBusinessId(businessId, full);
    }
}
