package fi.digitraffic.tis.vaco.company.service;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.Hierarchy;
import fi.digitraffic.tis.vaco.company.repository.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class CompanyService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final CompanyRepository companyRepository;
    private final CachingService cachingService;

    public CompanyService(CompanyRepository companyRepository, CachingService cachingService) {
        this.companyRepository = companyRepository;
        this.cachingService = cachingService;
    }

    public Optional<Company> createCompany(Company company) {
        Optional<Company> existing = companyRepository.findByBusinessId(company.businessId());
        if (existing.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(companyRepository.create(company));
    }

    public Optional<Company> findByBusinessId(String businessId) {
        return companyRepository.findByBusinessId(businessId);
    }

    public List<Company> listAllWithEntries() {
        return companyRepository.listAllWithEntries();
    }

    public Set<Company> findAllByAdGroupIds(List<String> adGroupIds) {
        return companyRepository.findAllByAdGroupIds(adGroupIds);
    }

    public Company updateAdGroupId(Company company, String groupId) {
        Company updated = companyRepository.updateAdGroupId(company, groupId);
        cachingService.invalidateCompanyHierarchy(company, "descendants");
        return updated;
    }

    public boolean isChildOfAny(Set<Company> possibleParents, String childBusinessId) {
        return Streams.map(possibleParents, this::findHierarchyFor)
            .filter(h -> h.isMember(childBusinessId))
            .findFirst()
            .isPresent();
    }

    public Map<String, Company> listAllChildren(Company parent) {
        Map<String, Company> allChildren = new HashMap<>();
        Hierarchy h = findHierarchyFor(parent);
        h.collectChildren(allChildren);
        return allChildren;
    }

    private Hierarchy findHierarchyFor(Company company) {
        return cachingService.cacheCompanyHierarchy(company, "descendants", key ->
            companyRepository.resolveDescendantHierarchyFor(company));
    }
}
