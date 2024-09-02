package fi.digitraffic.tis.vaco.featureflags;

import fi.digitraffic.tis.exceptions.PersistenceException;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.repositories.FeatureFlagRepository;
import fi.digitraffic.tis.vaco.featureflags.model.FeatureFlag;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class FeatureFlagsService {

    private final RecordMapper recordMapper;

    private final FeatureFlagRepository featureFlagRepository;

    public FeatureFlagsService(RecordMapper recordMapper,
                               FeatureFlagRepository featureFlagRepository) {
        this.recordMapper = Objects.requireNonNull(recordMapper);
        this.featureFlagRepository = Objects.requireNonNull(featureFlagRepository);
    }

    public List<FeatureFlag> listFeatureFlags() {
        return Streams.collect(featureFlagRepository.listFeatureFlags(), recordMapper::toFeatureFlag);
    }

    public Optional<FeatureFlag> findFeatureFlag(String name) {
        return featureFlagRepository.findFeatureFlag(name).map(recordMapper::toFeatureFlag);
    }

    public FeatureFlag setFeatureFlag(FeatureFlag featureFlag, boolean enabled, String modifierOid) {
        return featureFlagRepository.findFeatureFlag(featureFlag.name())
            .map(ffr -> featureFlagRepository.setFeatureFlag(ffr, enabled, modifierOid))
            .map(recordMapper::toFeatureFlag)
            .orElseThrow(() -> new PersistenceException("Unknown feature flag " + featureFlag));
    }

    public boolean isFeatureFlagEnabled(String name) {
        return featureFlagRepository.isFeatureFlagEnabled(name);
    }
}
