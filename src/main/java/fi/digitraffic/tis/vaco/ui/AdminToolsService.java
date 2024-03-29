package fi.digitraffic.tis.vaco.ui;

import com.opencsv.CSVWriter;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.me.MeService;
import fi.digitraffic.tis.vaco.ui.model.CompanyLatestEntry;
import fi.digitraffic.tis.vaco.ui.model.CompanyWithFormatSummary;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
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

    public String[] getDataDeliveryCsvHeaders(String language) {
        switch (language) {
            case "en" -> {
                return new String[]{"Business ID", "Company name", "Data URL",
                    "Feed name", "Format", "Converted format(-s)", "Status", "Date published", "Entry public ID"};
            }
            case "sv" -> {
                return new String[]{"Företags-id", "Företagsnamn", "Data-URL",
                    "Matningsnamn", "Format", "Konverterad", "Status", "Skapad", "Inlämnings-ID"};
            }
            default -> {
                return new String[]{"Y-tunnus", "Yrityksen nimi", "Datan URL",
                    "Syötteen nimi", "Formaatti", "Konversio", "Tila", "Julkaistu", "Julkaisun tunnus"};
            }
        }
    }

    public List<CompanyLatestEntry> getDataDeliveryOverview(@Nullable Set<Company> userCompanies) {
        Set<String> businessIds = userCompanies != null
            ? userCompanies.stream().map(Company::businessId).collect(Collectors.toSet())
            : null;
        return adminToolsRepository.getDataDeliveryOverview(businessIds);
    }

    public void exportDataDeliveryToCsv(CSVWriter csvWriter, String language) {
        csvWriter.writeNext(getDataDeliveryCsvHeaders(language));

        List<CompanyLatestEntry> data = adminToolsRepository.getDataDeliveryOverview(null);
        String currentBusinessId = "";
        for (CompanyLatestEntry entry : data) {
            boolean putEmptyString = false;
            if (currentBusinessId.equals(entry.businessId())) {
                putEmptyString = true;
            } else {
                currentBusinessId = entry.businessId();
            }

            csvWriter.writeNext(new String[]{
                putEmptyString ? "" : entry.businessId(),
                putEmptyString ? "" : entry.companyName(),
                entry.url(),
                entry.feedName(),
                entry.format(),
                entry.convertedFormat(),
                entry.status() != null ? entry.status().fieldName() : "",
                entry.created() != null ? entry.created().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")) : "",
                entry.publicId()
            });
        }
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
