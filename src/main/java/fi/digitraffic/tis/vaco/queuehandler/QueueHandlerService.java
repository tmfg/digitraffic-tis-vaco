package fi.digitraffic.tis.vaco.queuehandler;

import fi.digitraffic.tis.vaco.conversion.ConversionView;
import fi.digitraffic.tis.vaco.queuehandler.dto.Metadata;
import fi.digitraffic.tis.vaco.queuehandler.dto.PhaseView;
import fi.digitraffic.tis.vaco.queuehandler.dto.entry.EntryCommand;
import fi.digitraffic.tis.vaco.queuehandler.dto.entry.EntryStatus;
import fi.digitraffic.tis.vaco.queuehandler.dto.entry.EntryView;
import fi.digitraffic.tis.vaco.queuehandler.mapper.EntryMapper;
import fi.digitraffic.tis.vaco.queuehandler.mapper.PhaseMapper;
import fi.digitraffic.tis.vaco.queuehandler.model.*;
import fi.digitraffic.tis.vaco.queuehandler.mapper.ConversionInputMapper;
import fi.digitraffic.tis.vaco.queuehandler.repository.ConversionInputRepository;
import fi.digitraffic.tis.vaco.queuehandler.repository.EntryRepository;
import fi.digitraffic.tis.vaco.queuehandler.mapper.ValidationInputMapper;
import fi.digitraffic.tis.vaco.queuehandler.repository.PhaseRepository;
import fi.digitraffic.tis.vaco.queuehandler.repository.ValidationInputRepository;
import fi.digitraffic.tis.vaco.validation.ValidationView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class QueueHandlerService {

    @Autowired
    private ConversionInputRepository conversionInputRepository;

    @Autowired
    private ValidationInputRepository validationInputRepository;

    @Autowired
    private PhaseRepository phaseRepository;

    @Autowired
    private EntryRepository entryRepository;

    @Autowired
    private EntryMapper entryMapper;

    @Autowired
    private ValidationInputMapper validationInputMapper;

    @Autowired
    private ConversionInputMapper conversionInputMapper;

    @Autowired
    private PhaseMapper phaseMapper;

    @Transactional
    public String processQueueEntry(EntryCommand entryCommand) {
        String publicId = "temporary-" +  new Timestamp(System.currentTimeMillis()).getTime();
        Entry entry = entryMapper.fromMetadataToEntry(publicId, entryCommand.input());
        Entry savedEntry = entryRepository.save(entry);

        // This perhaps needs to go into own dedicated ValidationService:
        ValidationInput validationInput = validationInputMapper
            .fromValidationCommandToInput(savedEntry.id(), entryCommand.validation());
        validationInputRepository.save(validationInput);

        // This perhaps needs to go into own dedicated ConversionService later:
        ConversionInput conversionInput = conversionInputMapper
            .fromConversionCommandToInput(savedEntry.id(), entryCommand.conversion());
        conversionInputRepository.save(conversionInput);

        // No builder yet...
        Phase processingStarted = new Phase(null,
            savedEntry.id(),
            PhaseName.STARTED,
            new Timestamp(System.currentTimeMillis()));
        phaseRepository.save(processingStarted);

        return savedEntry.publicId();
    }

    public EntryView getQueueEntryView(String publicId) {
        // Instead of just entry, there will be another class in /model that
        // will encompass stuff from entry, conversion and validation:
        Entry entry = entryRepository.findByPublicId(publicId);
        Metadata metadata = entryMapper.fromEntryToMetadata(entry);
        // This needs to go into own dedicated PhaseService later:
        List<Phase> phases = phaseRepository.findByEntryId(entry.id());
        List<PhaseView> phaseViews = phases.stream()
            .map(phase -> phaseMapper.fromPhaseToPhaseView(phase))
            .collect(Collectors.toList());

        EntryStatus entryStatus = new EntryStatus("Test!", phaseViews);
        return  new EntryView(entryStatus, metadata, null, null);
    }
}
