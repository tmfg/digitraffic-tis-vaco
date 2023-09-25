package fi.digitraffic.tis.vaco.ruleset;

import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class RulesetService {
    private final RulesetRepository rulesetRepository;

    public RulesetService(RulesetRepository rulesetRepository) {
        this.rulesetRepository = rulesetRepository;
    }

    public Set<Ruleset> selectRulesets(String businessId, Type type, Set<String> names) {
        Set<Ruleset> rulesets;
        if (names.isEmpty()) {
            rulesets = rulesetRepository.findRulesets(businessId, type);
        } else {
            rulesets = rulesetRepository.findRulesets(businessId, type, names);
        }
        return rulesets;
    }
}
