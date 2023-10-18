package fi.digitraffic.tis.vaco.ruleset;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class RulesetService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final RulesetRepository rulesetRepository;

    public RulesetService(RulesetRepository rulesetRepository) {
        this.rulesetRepository = rulesetRepository;
    }

    public Set<Ruleset> selectRulesets(String businessId) {
        return rulesetRepository.findRulesets(businessId);
    }

    public Set<Ruleset> selectRulesets(String businessId, Type type, Set<String> names) {
        Set<Ruleset> rulesets;
        if (names.isEmpty()) {
            rulesets = rulesetRepository.findRulesets(businessId, type);
        } else {
            rulesets = rulesetRepository.findRulesets(businessId, type, names);
        }

        logger.info("Selected {} rulesets for {} are {}", type, businessId, Streams.collect(rulesets, Ruleset::identifyingName));

        return rulesets;
    }
}
