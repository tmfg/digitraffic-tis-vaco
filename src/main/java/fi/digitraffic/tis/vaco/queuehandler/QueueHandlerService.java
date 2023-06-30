package fi.digitraffic.tis.vaco.queuehandler;

import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableDelegationJobMessage;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.queuehandler.dto.ImmutableEntryCommand;
import fi.digitraffic.tis.vaco.queuehandler.mapper.EntryCommandMapper;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutablePhase;
import fi.digitraffic.tis.vaco.queuehandler.repository.QueueHandlerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class QueueHandlerService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final MessagingService messagingService;

    private final QueueHandlerRepository queueHandlerRepository;

    private final EntryCommandMapper entryCommandMapper;

    public QueueHandlerService(EntryCommandMapper entryCommandMapper,
                               MessagingService messagingService,
                               QueueHandlerRepository queueHandlerRepository) {
        this.entryCommandMapper = entryCommandMapper;
        this.messagingService = messagingService;
        this.queueHandlerRepository = queueHandlerRepository;
    }

    @Transactional
    public ImmutableEntry processQueueEntry(ImmutableEntryCommand entryCommand) {
        ImmutableEntry converted = entryCommandMapper.toQueueEntry(entryCommand);
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

    public ImmutablePhase reportPhase(ImmutablePhase phase, ProcessingState state) {
        logger.info("Updating phase {} to {}", phase, state);
        return switch (state) {
            case START -> queueHandlerRepository.startPhase(phase);
            case UPDATE -> queueHandlerRepository.updatePhase(phase);
            case COMPLETE -> queueHandlerRepository.completePhase(phase);
        };
    }

    public List<ImmutableEntry> getAllQueueEntriesFor(String businessId, boolean full) {
        return queueHandlerRepository.findAllByBusinessId(businessId, full);
    }
}
