package fi.digitraffic.tis.vaco.company.service;

import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.repository.CompanyRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
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
}