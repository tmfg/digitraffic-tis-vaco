package fi.digitraffic.tis.vaco.ruleset;

import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.repositories.RulesetRepository;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import fi.digitraffic.tis.vaco.ruleset.model.RulesetType;
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
    private final RecordMapper recordMapper;

    private final RulesetRepository rulesetRepository;

    public RulesetService(CachingService cachingService,
                          RulesetRepository rulesetRepository,
                          RecordMapper recordMapper) {
        this.cachingService = Objects.requireNonNull(cachingService);
        this.rulesetRepository = Objects.requireNonNull(rulesetRepository);
        this.recordMapper = Objects.requireNonNull(recordMapper);
    }

    public Set<Ruleset> selectRulesets(String businessId) {
        // For Public validation test, we don't want to have an actual "cooperation" with Fintraffic as a company,
        // but we still want to re-use same rulesets
        String actualBusinessId = Constants.PUBLIC_VALIDATION_TEST_ID.equals(businessId)
            ? Constants.FINTRAFFIC_BUSINESS_ID
            : businessId;
        return Streams.collect(rulesetRepository.findRulesets(actualBusinessId), recordMapper::toRuleset);
    }

    public Set<Ruleset> selectRulesets(String businessId, RulesetType type, TransitDataFormat format, Set<String> names) {
        Set<Ruleset> rulesets;
        if (names.isEmpty()) {
            rulesets = Streams.collect(rulesetRepository.findRulesets(businessId, format, type), recordMapper::toRuleset);
        } else {
            rulesets = Streams.collect(rulesetRepository.findRulesets(businessId, type, format, names), recordMapper::toRuleset);
        }

        logger.info("Selected {} {} rulesets for {} are {}, requested {}", format, type, businessId, Streams.collect(rulesets, Ruleset::identifyingName), names);

        return rulesets;
    }

    public Ruleset createRuleset(ImmutableRuleset ruleset) {
        return recordMapper.toRuleset(rulesetRepository.createRuleset(ruleset));
    }

    public void deleteRuleset(Ruleset ruleset) {
        rulesetRepository.deleteRuleset(ruleset);
        cachingService.invalidateRuleset(ruleset.identifyingName());
    }

    public Optional<Ruleset> findByName(String rulesetName) {
        return cachingService.cacheRuleset(
            rulesetName,
            name -> rulesetRepository.findByName(name).map(recordMapper::toRuleset).orElse(null));
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
        return rulesetRepository.anyPrerequisiteDependencyFailed(entry, r);
    }
}
