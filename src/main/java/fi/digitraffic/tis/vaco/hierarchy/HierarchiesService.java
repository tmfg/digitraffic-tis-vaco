package fi.digitraffic.tis.vaco.hierarchy;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.HierarchyType;
import fi.digitraffic.tis.vaco.db.UnknownEntityException;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.HierarchyRecord;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.db.repositories.HierarchiesRepository;
import fi.digitraffic.tis.vaco.hierarchy.model.Hierarchy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class HierarchiesService {

    private final CompanyRepository companyRepository;
    private final HierarchiesRepository hierarchiesRepository;
    private final RecordMapper recordMapper;

    public HierarchiesService(HierarchiesRepository hierarchiesRepository, RecordMapper recordMapper, CompanyRepository companyRepository) {
        this.hierarchiesRepository = hierarchiesRepository;
        this.recordMapper = recordMapper;
        this.companyRepository = companyRepository;
    }

    public List<Hierarchy> listAll() {
        return Streams.collect(
            hierarchiesRepository.listAll(),
            hierarchyRecord -> recordMapper.toHierarchy(hierarchyRecord, findCompany(hierarchyRecord)));
    }

    public Optional<Hierarchy> createHierarchy(Company company, HierarchyType type) {
        return companyRepository.findByBusinessId(company.businessId())
            .flatMap(cr -> hierarchiesRepository.createHierarchy(cr, type))
            .map(hr -> recordMapper.toHierarchy(hr, findCompany(hr)));
    }

    private CompanyRecord findCompany(HierarchyRecord hierarchyRecord) {
        return companyRepository
            .findById(hierarchyRecord.rootCompanyId())
            .orElseThrow(() -> new UnknownEntityException(
                Long.toString(hierarchyRecord.rootCompanyId()),
                String.format(
                    "Invalid company database id '%s' for hierarchy %s!",
                    hierarchyRecord.rootCompanyId(),
                    hierarchyRecord.id())));
    }
}
