package fi.digitraffic.tis.vaco.company.service;

import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.Hierarchy;
import fi.digitraffic.tis.vaco.company.model.Partnership;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.company.service.model.LightweightHierarchy;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.PartnershipRecord;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.db.repositories.PartnershipRepository;
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
    private final PartnershipRepository partnershipRepository;
    private final RecordMapper recordMapper;

    /**
     * This service keeps an in-memory version of the company hierarchies always available to make tree navigation
     * related queries as fast and flexible as possible. Pay attention to caching and reloading!
     * <p>
     * The structure contains only business ids, if you need the entities, load them from the database.
     */
    private Map<String, LightweightHierarchy> hierarchies;

    public CompanyHierarchyService(CompanyRepository companyRepository, RecordMapper recordMapper, PartnershipRepository partnershipRepository) {
        this.companyRepository = Objects.requireNonNull(companyRepository);
        reloadRootHierarchies();
        this.recordMapper = recordMapper;
        this.partnershipRepository = partnershipRepository;
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
        Optional<CompanyRecord> existing = companyRepository.findByBusinessId(company.businessId());
        if (existing.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(companyRepository.create(company)).map(recordMapper::toCompany);
    }

    public Company editCompany(String businessId,Company company) {
        return companyRepository.update(businessId, company);
    }

    public Optional<Company> findByBusinessId(String businessId) {
        return companyRepository.findByBusinessId(businessId).map(recordMapper::toCompany);
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
        return companyRepository.findByBusinessId(company.businessId())
            .map(c -> companyRepository.updateAdGroupId(c, groupId))
            .map(recordMapper::toCompany)
            .get();  // TODO: feels slightly wrong but works
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
        if (findPartnership(partnershipType, partnerA, partnerB).isPresent()) {
            return Optional.empty();
        }
        Optional<CompanyRecord> pA = companyRepository.findByBusinessId(partnerA.businessId());
        Optional<CompanyRecord> pB = companyRepository.findByBusinessId(partnerB.businessId());
        if (pA.isPresent() && pB.isPresent()) {
            PartnershipRecord partnership = partnershipRepository.create(partnershipType, pA.get(), pB.get());
            reloadRootHierarchies();
            return Optional.of(recordMapper.toPartnership(partnership,
                                                          id -> recordMapper.toCompany(pA.get()),
                                                          id -> recordMapper.toCompany(pB.get())));
        } else {
            return Optional.empty();
        }
    }

    public List<Hierarchy> createPartnershipAndReturnUpdatedHierarchy(PartnershipType partnershipType, Company partnerA, Company partnerB) {
        return createPartnership(partnershipType, partnerA, partnerB)
            .map(p -> getHierarchiesContainingCompany(partnerB.businessId()))
            .orElse(List.of());  // TODO: This feels a bit weird, but probably works
    }

    public Optional<Partnership> findPartnership(PartnershipType partnershipType, Company partnerA, Company partnerB) {
        Optional<CompanyRecord> a = companyRepository.findByBusinessId(partnerA.businessId());
        Optional<CompanyRecord> b = companyRepository.findByBusinessId(partnerB.businessId());
        if (a.isPresent() && b.isPresent()) {
            return partnershipRepository.findByIds(partnershipType, a.get(), b.get())
                .map(p -> recordMapper.toPartnership(p,
                                                     id -> recordMapper.toCompany(a.get()),
                                                     id -> recordMapper.toCompany(b.get())));
        } else {
            return Optional.empty();
        }
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
        partnershipRepository.deletePartnership(partnership);
        reloadRootHierarchies();
        return getHierarchiesContainingCompany(partnership.partnerB().businessId());
    }

}
