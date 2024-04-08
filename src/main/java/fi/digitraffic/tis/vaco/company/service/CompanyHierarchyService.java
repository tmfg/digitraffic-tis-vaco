package fi.digitraffic.tis.vaco.company.service;

import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.Hierarchy;
import fi.digitraffic.tis.vaco.company.model.ImmutablePartnership;
import fi.digitraffic.tis.vaco.company.model.Partnership;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.company.service.model.LightweightHierarchy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class CompanyHierarchyService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final CompanyRepository companyRepository;

    /**
     * This service keeps an in-memory version of the company hierarchies always available to make tree navigation
     * related queries as fast and flexible as possible. Pay attention to caching and reloading!
     * <p>
     * The structure contains only business ids, if you need the entities, load them from the database.
     */
    private Map<String, LightweightHierarchy> hierarchies;

    public CompanyHierarchyService(CompanyRepository companyRepository) {
        this.companyRepository = Objects.requireNonNull(companyRepository);
        reloadRootHierarchies();
    }

    /**
     * Reloads all root hierarchies from database and updates in-memory lookups.
     */
    private void reloadRootHierarchies() {
        Map<Company, Hierarchy> rootHierarchies = companyRepository.findRootHierarchies();
        Map<String, LightweightHierarchy> lightweightHierarchies = new HashMap<>();
        rootHierarchies.forEach((company, hierarchy) ->
            lightweightHierarchies.put(company.businessId(), LightweightHierarchy.from(hierarchy)));
        synchronized (this) {
            this.hierarchies = lightweightHierarchies;
        }
    }

    public Optional<Company> createCompany(Company company) {
        Optional<Company> existing = companyRepository.findByBusinessId(company.businessId());
        if (existing.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(companyRepository.create(company));
    }

    public Company editCompany(String businessId,Company company) {
        return companyRepository.update(businessId, company);
    }

    public Optional<Company> findByBusinessId(String businessId) {
        return companyRepository.findByBusinessId(businessId);
    }

    public Company getPublicTestCompany() {
        return findByBusinessId(Constants.PUBLIC_VALIDATION_TEST_ID).get();
    }

    public List<Company> listAllWithEntries() {
        return companyRepository.listAllWithEntries();
    }

    public Set<Company> findAllByAdGroupIds(List<String> adGroupIds) {
        return companyRepository.findAllByAdGroupIds(adGroupIds);
    }

    public Company updateAdGroupId(Company company, String groupId) {
        return companyRepository.updateAdGroupId(company, groupId);
    }

    public boolean isChildOfAny(Set<Company> possibleParents, String childBusinessId) {
        List<LightweightHierarchy> possibleHierarchies = new ArrayList<>();
        for (Company possibleParent : possibleParents) {
            for (LightweightHierarchy rootHierarchy : hierarchies.values()) {
                Optional<LightweightHierarchy> found = rootHierarchy.findNode(possibleParent.businessId());
                found.ifPresent(possibleHierarchies::add);
            }
        }
        for (LightweightHierarchy h : possibleHierarchies) {
            if (h.hasChildWithBusinessId(childBusinessId)) {
                return true;
            }
        }
        return false;
    }

    public List<Hierarchy> getHierarchiesContainingCompany(String selectedBusinessId) {
        List<Hierarchy> fullHierarchies = new ArrayList<>();
        Map<String, Company> companiesByBusinessId = companyRepository.findAlLByIds();

        hierarchies.keySet().forEach(businessId -> {
            LightweightHierarchy lightweightHierarchy = hierarchies.get(businessId);
            if (lightweightHierarchy.isMember(selectedBusinessId)) {
                Hierarchy hierarchy = lightweightHierarchy.toTruncatedHierarchy(companiesByBusinessId, selectedBusinessId, false);
                fullHierarchies.add(hierarchy);
            }
        });

        return fullHierarchies;
    }

    public List<Hierarchy> getAllHierarchies() {
        List<Hierarchy> fullHierarchies = new ArrayList<>();
        Map<String, Company> companiesByBusinessId = companyRepository.findAlLByIds();

        hierarchies.keySet().forEach(businessId -> {
            Hierarchy hierarchy = hierarchies.get(businessId).toHierarchy(companiesByBusinessId);
            fullHierarchies.add(hierarchy);
        });

        return fullHierarchies;
    }

    public List<Hierarchy> getHierarchies(String businessId) {
        return businessId != null && !businessId.isBlank()
            ? getHierarchiesContainingCompany(businessId)
            : getAllHierarchies();
    }

    public Map<String, String> listAllChildren(Company parent) {
        Map<String, String> allChildren = new HashMap<>();
        for (LightweightHierarchy h : hierarchies.values()) {
            h.findNode(parent.businessId())
                .ifPresent(n -> n.collectChildren(allChildren));
        }
        return allChildren;
    }

    public Optional<Partnership> createPartnership(PartnershipType partnershipType, Company partnerA, Company partnerB) {
        if (findPartnership(partnerA, partnerB).isPresent()) {
            return Optional.empty();
        }
        Optional<Partnership> partnership = Optional.of(companyRepository.create(
            ImmutablePartnership.of(
                partnershipType,
                partnerA,
                partnerB)));
        reloadRootHierarchies();
        return partnership;
    }

    public List<Hierarchy> createPartnershipAndReturnUpdatedHierarchy(PartnershipType partnershipType, Company partnerA, Company partnerB) {
        companyRepository.create(
            ImmutablePartnership.of(
                partnershipType,
                partnerA,
                partnerB));
        reloadRootHierarchies();
        return getHierarchiesContainingCompany(partnerB.businessId());
    }

    public Optional<Partnership> findPartnership(PartnershipType partnershipType, Company partnerA, Company partnerB) {
        return companyRepository.findByIds(partnershipType, partnerA.id(), partnerB.id());
    }

    @Transactional
    public List<Hierarchy> swapPartnership(Company newPartnerA, Company partnerB,
                                           Partnership partnershipToDelete) {
        deletePartnership(partnershipToDelete);
        createPartnership(PartnershipType.AUTHORITY_PROVIDER, newPartnerA, partnerB);
        reloadRootHierarchies();
        return getHierarchiesContainingCompany(partnerB.businessId());
    }

    public List<Hierarchy> deletePartnership(Partnership partnership) {
        companyRepository.deletePartnership(partnership);
        reloadRootHierarchies();
        return getHierarchiesContainingCompany(partnership.partnerB().businessId());
    }

    public Optional<Partnership> findPartnership(Company from, Company to) {
        return companyRepository.findByIds(PartnershipType.AUTHORITY_PROVIDER, from.id(), to.id());
    }
}
