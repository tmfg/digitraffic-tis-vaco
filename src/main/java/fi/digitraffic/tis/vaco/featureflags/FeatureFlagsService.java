package fi.digitraffic.tis.vaco.featureflags;

import fi.digitraffic.tis.vaco.db.repositories.FeatureFlagsRepository;
import fi.digitraffic.tis.vaco.featureflags.model.FeatureFlag;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class FeatureFlagsService {
    private final FeatureFlagsRepository featureFlagsRepository;

    public FeatureFlagsService(FeatureFlagsRepository featureFlagsRepository) {
        this.featureFlagsRepository = Objects.requireNonNull(featureFlagsRepository);
    }

    public List<FeatureFlag> listFeatureFlags() {
        return featureFlagsRepository.listFeatureFlags();
    }

    public Optional<FeatureFlag> findFeatureFlag(String name) {
        return featureFlagsRepository.findFeatureFlag(name);
    }

    public FeatureFlag setFeatureFlag(FeatureFlag featureFlag, boolean enabled, String modifierOid) {
        return featureFlagsRepository.setFeatureFlag(featureFlag, enabled, modifierOid);
    }

    public boolean isFeatureFlagEnabled(String name) {
        return featureFlagsRepository.isFeatureFlagEnabled(name);
    }
}
