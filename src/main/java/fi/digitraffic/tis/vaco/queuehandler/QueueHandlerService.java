package fi.digitraffic.tis.vaco.queuehandler;

import fi.digitraffic.tis.vaco.conversion.ConversionView;
import fi.digitraffic.tis.vaco.messaging.model.JobDescription;
import fi.digitraffic.tis.vaco.aws.SqsMessagingService;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.queuehandler.dto.PhaseView;
import fi.digitraffic.tis.vaco.queuehandler.dto.entry.EntryCommand;
import fi.digitraffic.tis.vaco.queuehandler.dto.entry.EntryResult;
import fi.digitraffic.tis.vaco.queuehandler.dto.entry.EntryStatus;
import fi.digitraffic.tis.vaco.queuehandler.dto.entry.EntryView;
import fi.digitraffic.tis.vaco.queuehandler.mapper.ConversionInputMapper;
import fi.digitraffic.tis.vaco.queuehandler.mapper.PhaseMapper;
import fi.digitraffic.tis.vaco.queuehandler.mapper.ValidationInputMapper;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.Phase;
import fi.digitraffic.tis.vaco.queuehandler.model.PhaseName;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.queuehandler.repository.ConversionInputRepository;
import fi.digitraffic.tis.vaco.queuehandler.repository.EntryRepository;
import fi.digitraffic.tis.vaco.queuehandler.repository.PhaseRepository;
import fi.digitraffic.tis.vaco.queuehandler.repository.ValidationInputRepository;
import fi.digitraffic.tis.vaco.validation.ValidationView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class QueueHandlerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueueHandlerService.class);

    private final ConversionInputRepository conversionInputRepository;

    private final ValidationInputRepository validationInputRepository;

    private final PhaseRepository phaseRepository;

    private final EntryRepository entryRepository;

    private final ValidationInputMapper validationInputMapper;

    private final ConversionInputMapper conversionInputMapper;

    private final PhaseMapper phaseMapper;

    private final SqsMessagingService messagingService;

    public QueueHandlerService(ConversionInputRepository conversionInputRepository,
                               ValidationInputRepository validationInputRepository,
                               PhaseRepository phaseRepository,
                               EntryRepository entryRepository,
                               ValidationInputMapper validationInputMapper,
                               ConversionInputMapper conversionInputMapper,
                               PhaseMapper phaseMapper,
                               SqsMessagingService messagingService) {
        this.conversionInputRepository = conversionInputRepository;
        this.validationInputRepository = validationInputRepository;
        this.phaseRepository = phaseRepository;
        this.entryRepository = entryRepository;
        this.validationInputMapper = validationInputMapper;
        this.conversionInputMapper = conversionInputMapper;
        this.phaseMapper = phaseMapper;
        this.messagingService = messagingService;
    }

    @Transactional
    public String processQueueEntry(EntryCommand entryCommand) {
        // TODO: fix nanoid generation
        String publicId = "temporary-" + new Timestamp(System.currentTimeMillis()).getTime();
        // No builder yet ;(
        Entry entry = new Entry(null,
            publicId,
            entryCommand.format(),
            entryCommand.url(),
            entryCommand.etag(),
            entryCommand.metadata());
        Entry savedEntry = entryRepository.save(entry);

        // TODO: This perhaps needs to go into own dedicated ValidationService:
        ValidationInput validationInput = validationInputMapper
            .fromValidationCommandToInput(savedEntry.id(), entryCommand.validation());
        validationInputRepository.save(validationInput);

        // TODO: This perhaps needs to go into own dedicated ConversionService later:
        ConversionInput conversionInput = conversionInputMapper
            .fromConversionCommandToInput(savedEntry.id(), entryCommand.conversion());
        conversionInputRepository.save(conversionInput);

        // No builder yet...
        Phase processingStarted = new Phase(null,
            savedEntry.id(),
            PhaseName.STARTED,
            new Timestamp(System.currentTimeMillis()));
        phaseRepository.save(processingStarted);

        messagingService.sendMessage(MessageQueue.JOBS, new JobDescription("Hello", null));

        return savedEntry.publicId();
    }

    public EntryResult getQueueEntryView(String publicId) {
        Entry entry = entryRepository.findByPublicId(publicId);

        // TODO: This needs to go into own dedicated PhaseService later:
        List<Phase> phases = phaseRepository.findByEntryId(entry.id());
        List<PhaseView> phaseViews = phases.stream()
            .map(phaseMapper::fromPhaseToPhaseView)
            .collect(Collectors.toList());
        EntryStatus entryStatus = new EntryStatus("Test!", phaseViews);

        EntryView entryView = new EntryView(entry.format(),
            entry.url(),
            entry.etag(),
            new ValidationView(),
            new ConversionView(),
            entry.metadata());

        return new EntryResult(entryStatus, new ArrayList<>(), entryView);
    }
}
