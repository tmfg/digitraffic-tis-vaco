package fi.digitraffic.tis.vaco.company.service;

import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.Hierarchy;
import fi.digitraffic.tis.vaco.company.model.ImmutablePartnership;
import fi.digitraffic.tis.vaco.company.model.Partnership;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.company.repository.CompanyHierarchyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    private final CompanyHierarchyRepository companyHierarchyRepository;

    /**
     * This service keeps an in-memory version of the company hierarchies always available to make tree navigation
     * related queries as fast and flexible as possible. Pay attention to caching and reloading!
     */
    private Map<Company, Hierarchy> hierarchies;

    /**
     * A lookup of child -> parent is kept as well
     */

    public CompanyHierarchyService(CompanyHierarchyRepository companyHierarchyRepository) {
        this.companyHierarchyRepository = Objects.requireNonNull(companyHierarchyRepository);
        reloadRootHierarchies();
    }

    /**
     * Reloads all root hierarchies from database and updates in-memory lookups.
     */
    public void reloadRootHierarchies() {
        Map<Company, Hierarchy> rootHierarchies = companyHierarchyRepository.findRootHierarchies();
        synchronized (this) {
            this.hierarchies = rootHierarchies;
        }
    }

    public Optional<Company> createCompany(Company company) {
        Optional<Company> existing = companyHierarchyRepository.findByBusinessId(company.businessId());
        if (existing.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(companyHierarchyRepository.create(company));
    }

    public Optional<Company> findByBusinessId(String businessId) {
        return companyHierarchyRepository.findByBusinessId(businessId);
    }

    public List<Company> listAllWithEntries() {
        return companyHierarchyRepository.listAllWithEntries();
    }

    public Set<Company> findAllByAdGroupIds(List<String> adGroupIds) {
        return companyHierarchyRepository.findAllByAdGroupIds(adGroupIds);
    }

    public Company updateAdGroupId(Company company, String groupId) {
        return companyHierarchyRepository.updateAdGroupId(company, groupId);
    }

    public boolean isChildOfAny(Set<Company> possibleParents, String childBusinessId) {
        List<Hierarchy> possibleHierarchies = new ArrayList<>();
        for (Company possibleParent : possibleParents) {
            for (Hierarchy rootHierarchy : hierarchies.values()) {
                Optional<Hierarchy> found = rootHierarchy.findNode(possibleParent.businessId());
                found.ifPresent(possibleHierarchies::add);
            }
        }
        for (Hierarchy h : possibleHierarchies) {
            if (h.hasChildWithBusinessId(childBusinessId)) {
                return true;
            }
        }
        return false;
    }

    public Map<String, Company> listAllChildren(Company parent) {
        Map<String, Company> allChildren = new HashMap<>();
        for (Hierarchy h : hierarchies.values()) {
            h.findNode(parent.businessId())
                .ifPresent(n -> n.collectChildren(allChildren));
        }
        return allChildren;
    }

    public Optional<Partnership> createPartnership(PartnershipType partnershipType, Company partnerA, Company partnerB) {
        if (companyHierarchyRepository.findByIds(partnershipType, partnerA.id(), partnerB.id()).isPresent()) {
            return Optional.empty();
        }
        reloadRootHierarchies();
        return Optional.of(companyHierarchyRepository.create(
            ImmutablePartnership.of(
                partnershipType,
                partnerA,
                partnerB)));
    }
}
