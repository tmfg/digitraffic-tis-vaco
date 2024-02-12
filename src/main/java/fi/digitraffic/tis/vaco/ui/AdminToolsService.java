package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.ui.model.CompanyLatestEntry;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminToolsService {
    private final AdminToolsRepository adminToolsRepository;

    public AdminToolsService(AdminToolsRepository adminToolsRepository) {
        this.adminToolsRepository = Objects.requireNonNull(adminToolsRepository);
    }

    public List<CompanyLatestEntry> listLatestEntriesPerCompany(@Nullable Set<Company> userCompanies) {
        Set<String> businessIds = userCompanies != null
            ? userCompanies.stream().map(Company::businessId).collect(Collectors.toSet())
            : null;
        return adminToolsRepository.listCompanyLatestEntries(businessIds);
    }
}
