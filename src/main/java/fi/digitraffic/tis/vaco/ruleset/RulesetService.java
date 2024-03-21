package fi.digitraffic.tis.vaco.ruleset;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class RulesetService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final CachingService cachingService;

    private final RulesetRepository rulesetRepository;

    public RulesetService(CachingService cachingService,
                          RulesetRepository rulesetRepository) {
        this.cachingService = Objects.requireNonNull(cachingService);
        this.rulesetRepository = Objects.requireNonNull(rulesetRepository);
    }

    public Set<Ruleset> selectRulesets(String businessId) {
        return rulesetRepository.findRulesets(businessId);
    }

    public Set<Ruleset> selectRulesets(String businessId, Type type, TransitDataFormat format, Set<String> names) {
        Set<Ruleset> rulesets;
        if (names.isEmpty()) {
            rulesets = rulesetRepository.findRulesets(businessId, format, type);
        } else {
            rulesets = rulesetRepository.findRulesets(businessId, type, format, names);
        }

        logger.info("Selected {} rulesets for {} are {}, requested {}", type, businessId, Streams.collect(rulesets, Ruleset::identifyingName), names);

        return rulesets;
    }

    public Ruleset createRuleset(ImmutableRuleset ruleset) {
        return rulesetRepository.createRuleset(ruleset);
    }

    public void deleteRuleset(Ruleset ruleset) {
        rulesetRepository.deleteRuleset(ruleset);
        cachingService.invalidateRuleset(ruleset.identifyingName());
    }

    public Optional<Ruleset> findByName(String rulesetName) {
        return cachingService.cacheRuleset(
            rulesetName,
            name -> rulesetRepository.findByName(name).orElse(null));
    }

    /**
     * Returns all ruleset names in no particular order. This method should be treated as internal only as it exposes
     * every single external rule registered in database without filters.
     *
     * @return Set of known rulesets.
     */
    public Set<String> listAllNames() {
        return rulesetRepository.listAllNames();
    }

    public boolean dependenciesCompletedSuccessfully(Entry entry, Ruleset r) {
        return rulesetRepository.anyDependencyFailed(entry, r);
    }
}
