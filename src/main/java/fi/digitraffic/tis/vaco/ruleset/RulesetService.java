package fi.digitraffic.tis.vaco.ruleset;

import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.db.repositories.RulesetAccessRepository;
import fi.digitraffic.tis.vaco.db.repositories.RulesetRepository;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.RulesetRecord;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.RulesetType;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class RulesetService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final CachingService cachingService;
    private final RecordMapper recordMapper;

    private final RulesetRepository rulesetRepository;
    private final RulesetAccessRepository rulesetAccessRepository;
    private final CompanyRepository companyRepository;

    public RulesetService(CachingService cachingService,
                          RulesetRepository rulesetRepository,
                          RulesetAccessRepository rulesetAccessRepository,
                          CompanyRepository companyRepository,
                          RecordMapper recordMapper) {
        this.cachingService = Objects.requireNonNull(cachingService);
        this.rulesetRepository = Objects.requireNonNull(rulesetRepository);
        this.rulesetAccessRepository = Objects.requireNonNull(rulesetAccessRepository);
        this.companyRepository = Objects.requireNonNull(companyRepository);
        this.recordMapper = Objects.requireNonNull(recordMapper);
    }

    public Set<Ruleset> findCompanyRulesets(String businessId) {
        // For Public validation test, we don't want to have an actual "cooperation" with Fintraffic as a company,
        // but we still want to re-use same rulesets
        String actualBusinessId = Constants.PUBLIC_VALIDATION_TEST_ID.equals(businessId)
            ? Constants.FINTRAFFIC_BUSINESS_ID
            : businessId;
        return Streams.collect(rulesetRepository.findRulesets(actualBusinessId), recordMapper::toRuleset);
    }

    public Set<Ruleset> findCompanyRulesets(String businessId, RulesetType type, TransitDataFormat format, Set<String> names) {
        Set<Ruleset> rulesets;
        if (names.isEmpty()) {
            rulesets = Streams.collect(rulesetRepository.findRulesets(businessId, format, type), recordMapper::toRuleset);
        } else {
            rulesets = Streams.collect(rulesetRepository.findRulesets(businessId, type, format, names), recordMapper::toRuleset);
        }

        logger.info("Selected {} {} rulesets for {} are {}, requested {}", format, type, businessId, Streams.collect(rulesets, Ruleset::identifyingName), names);

        return rulesets;
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
        return rulesetRepository.allPrerequisiteDependenciesCompletedSuccessfully(entry, r);
    }

    public boolean dependenciesProcessing(Entry entry, Ruleset r) {
        return rulesetRepository.areDependenciesProcessing(entry, r);
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
