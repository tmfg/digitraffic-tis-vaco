package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.me.MeService;
import fi.digitraffic.tis.vaco.ui.model.CompanyLatestEntry;
import fi.digitraffic.tis.vaco.ui.model.CompanyWithFormatSummary;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminToolsService {
    private final AdminToolsRepository adminToolsRepository;
    private final MeService meService;

    public AdminToolsService(AdminToolsRepository adminToolsRepository, MeService meService) {
        this.adminToolsRepository = Objects.requireNonNull(adminToolsRepository);
        this.meService = Objects.requireNonNull(meService);
    }

    public List<CompanyLatestEntry> listLatestEntriesPerCompany(@Nullable Set<Company> userCompanies) {
        Set<String> businessIds = userCompanies != null
            ? userCompanies.stream().map(Company::businessId).collect(Collectors.toSet())
            : null;
        return adminToolsRepository.listCompanyLatestEntries(businessIds);
    }

    public List<CompanyWithFormatSummary> getCompaniesWithFormatInfos() {
        List<CompanyWithFormatSummary> all = adminToolsRepository.getCompaniesWithFormats();
        if (meService.isAdmin()) {
            return all;
        } else {
            Set<String> userCompanies = Streams.collect(meService.findCompanies(), Company::businessId);
            return all.stream().filter(c -> userCompanies.contains(c.businessId())).toList();
        }
    }
}
