package fi.digitraffic.tis.vaco.process;

import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.process.model.ImmutablePhase;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PhaseService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final PhaseRepository phaseRepository;

    public PhaseService(PhaseRepository phaseRepository) {
        this.phaseRepository = phaseRepository;
    }

    public ImmutablePhase reportPhase(ImmutablePhase phase, ProcessingState state) {
        logger.info("Updating phase {} to {}", phase, state);
        return switch (state) {
            case START -> phaseRepository.startPhase(phase);
            case UPDATE -> phaseRepository.updatePhase(phase);
            case COMPLETE -> phaseRepository.completePhase(phase);
        };
    }

    public ImmutablePhase findPhase(Long entryId, String phaseName) {
        return phaseRepository.findPhase(entryId, phaseName);
    }

    public boolean createPhases(List<ImmutablePhase> phases) {
        return phaseRepository.createPhases(phases);
    }

    public List<ImmutablePhase> findPhases(Entry entry) {
        return phaseRepository.findPhases(entry.id());
    }
}
