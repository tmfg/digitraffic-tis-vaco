package fi.digitraffic.tis.vaco.queuehandler;

import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableJobDescription;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.queuehandler.dto.entry.EntryCommand;
import fi.digitraffic.tis.vaco.queuehandler.mapper.EntryCommandMapper;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutablePhase;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableQueueEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.PhaseState;
import fi.digitraffic.tis.vaco.queuehandler.repository.QueueHandlerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class QueueHandlerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueueHandlerService.class);

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
    public ImmutableQueueEntry processQueueEntry(EntryCommand entryCommand) {
        ImmutableQueueEntry converted = entryCommandMapper.toQueueEntry(entryCommand);
        ImmutableQueueEntry result = queueHandlerRepository.create(converted);

        ImmutableJobDescription job = ImmutableJobDescription.builder().message(result).build();
        messagingService.sendMessage(MessageQueue.JOBS, job);

        return result;
    }

    public Optional<ImmutableQueueEntry> getQueueEntryView(String publicId) {
        Optional<ImmutableQueueEntry> result = queueHandlerRepository.findByPublicId(publicId);

        return result;
    }

    public ImmutablePhase reportPhase(Long entryId, ImmutablePhase phase, PhaseState state) {
        return switch (state) {
            case START -> queueHandlerRepository.startPhase(phase.withEntryId(entryId));
            case UPDATED -> null;
            case COMPLETE -> queueHandlerRepository.completePhase(phase);
        };
    }
}
