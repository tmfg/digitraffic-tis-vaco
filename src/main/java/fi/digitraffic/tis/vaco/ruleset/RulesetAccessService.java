package fi.digitraffic.tis.vaco.ruleset;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.RulesetRecord;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.db.repositories.RulesetAccessRepository;
import fi.digitraffic.tis.vaco.db.repositories.RulesetRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Manages ruleset access grants: which companies are directly granted access to a ruleset.
 * See {@link fi.digitraffic.tis.vaco.db.repositories.RulesetRepository#findRulesets(String)}
 * for how these grants are resolved together with the partnership inheritance.
 */
@Service
public class RulesetAccessService {

    private final RulesetRepository rulesetRepository;
    private final RulesetAccessRepository rulesetAccessRepository;
    private final CompanyRepository companyRepository;
    private final RecordMapper recordMapper;

    public RulesetAccessService(RulesetRepository rulesetRepository,
                                RulesetAccessRepository rulesetAccessRepository,
                                CompanyRepository companyRepository,
                                RecordMapper recordMapper) {
        this.rulesetRepository = Objects.requireNonNull(rulesetRepository);
        this.rulesetAccessRepository = Objects.requireNonNull(rulesetAccessRepository);
        this.companyRepository = Objects.requireNonNull(companyRepository);
        this.recordMapper = Objects.requireNonNull(recordMapper);
    }

    /**
     * Grants a company access to a specific ruleset. Idempotent — returns {@code false} if the
     * grant already exists (no-op).
     */
    public boolean grantAccess(String businessId, String rulesetName) {
        Optional<CompanyRecord> company = companyRepository.findByBusinessId(businessId);
        Optional<RulesetRecord> ruleset = rulesetRepository.findByName(rulesetName);
        if (company.isEmpty() || ruleset.isEmpty()) {
            return false;
        }
        return rulesetAccessRepository.grantAccess(company.get(), ruleset.get());
    }

    /**
     * Revokes a company's direct access grant to a specific ruleset.
     */
    public void revokeAccess(String businessId, String rulesetName) {
        Optional<CompanyRecord> company = companyRepository.findByBusinessId(businessId);
        Optional<RulesetRecord> ruleset = rulesetRepository.findByName(rulesetName);
        if (company.isPresent() && ruleset.isPresent()) {
            rulesetAccessRepository.revokeAccess(company.get(), ruleset.get());
        }
    }

    /**
     * Lists companies directly granted access to the given ruleset. Returns {@link Optional#empty()} if the
     * ruleset doesn't exist.
     */
    public Optional<List<Company>> listGrants(String rulesetName) {
        return rulesetRepository.findByName(rulesetName)
            .map(ruleset -> Streams.map(rulesetAccessRepository.findGrantsForRuleset(ruleset), recordMapper::toCompany).toList());
    }
}
