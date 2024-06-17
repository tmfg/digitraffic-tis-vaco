package fi.digitraffic.tis.vaco.featureflags;

import fi.digitraffic.tis.exceptions.PersistenceException;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.repositories.FeatureFlagsRepository;
import fi.digitraffic.tis.vaco.featureflags.model.FeatureFlag;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class FeatureFlagsService {

    private final RecordMapper recordMapper;

    private final FeatureFlagsRepository featureFlagsRepository;

    public FeatureFlagsService(RecordMapper recordMapper,
                               FeatureFlagsRepository featureFlagsRepository) {
        this.recordMapper = Objects.requireNonNull(recordMapper);
        this.featureFlagsRepository = Objects.requireNonNull(featureFlagsRepository);
    }

    public List<FeatureFlag> listFeatureFlags() {
        return Streams.collect(featureFlagsRepository.listFeatureFlags(), recordMapper::toFeatureFlag);
    }

    public Optional<FeatureFlag> findFeatureFlag(String name) {
        return featureFlagsRepository.findFeatureFlag(name).map(recordMapper::toFeatureFlag);
    }

    public FeatureFlag setFeatureFlag(FeatureFlag featureFlag, boolean enabled, String modifierOid) {
        return featureFlagsRepository.findFeatureFlag(featureFlag.name())
            .map(ffr -> featureFlagsRepository.setFeatureFlag(ffr, enabled, modifierOid))
            .map(recordMapper::toFeatureFlag)
            .orElseThrow(() -> new PersistenceException("Unknown feature flag " + featureFlag));
    }

    public boolean isFeatureFlagEnabled(String name) {
        return featureFlagsRepository.isFeatureFlagEnabled(name);
    }
}
