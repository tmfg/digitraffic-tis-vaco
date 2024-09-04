package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.me.MeService;
import fi.digitraffic.tis.vaco.ui.model.CompanyLatestEntry;
import fi.digitraffic.tis.vaco.ui.model.CompanyWithFormatSummary;
import jakarta.annotation.Nullable;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminToolsService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final AdminToolsRepository adminToolsRepository;
    private final MeService meService;

    public AdminToolsService(AdminToolsRepository adminToolsRepository, MeService meService) {
        this.adminToolsRepository = Objects.requireNonNull(adminToolsRepository);
        this.meService = Objects.requireNonNull(meService);
    }

    public String[] getDataDeliveryCsvHeaders(String language) {
        switch (language) {
            case "en" -> {
                return new String[]{"Business ID", "Company name", "Context identifier", "Data URL",
                    "Feed name", "Format", "Converted format(-s)", "Status", "Date published", "Entry public ID"};
            }
            case "sv" -> {
                return new String[]{"Företags-id", "Företagsnamn", "Kontextidentifiare", "Data-URL",
                    "Matningsnamn", "Format", "Konverterad", "Status", "Skapad", "Inlämnings-ID"};
            }
            default -> {
                return new String[]{"Y-tunnus", "Yrityksen nimi", "Kontekstitunniste", "Datan URL",
                    "Syötteen nimi", "Formaatti", "Konversio", "Tila", "Julkaistu", "Julkaisun tunnus"};
            }
        }
    }

    public String getPublicValidationTestName(String language) {
        switch (language) {
            case "en" -> {
                return "Public validation test";
            }
            case "sv" -> {
                return "Offentligt valideringstest";
            }
            default -> {
                return "Julkinen validointitesti";
            }
        }
    }

    public List<CompanyLatestEntry> getDataDeliveryOverview(@Nullable Set<Company> userCompanies) {
        Set<String> businessIds = userCompanies != null
            ? userCompanies.stream().map(Company::businessId).collect(Collectors.toSet())
            : null;
        return adminToolsRepository.getDataDeliveryOverview(businessIds);
    }

    public void exportDataDeliveryToCsv(OutputStream outputStream, String language) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
            .setHeader(getDataDeliveryCsvHeaders(language))
            .build();

        try (CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(outputStream), format)) {
            List<CompanyLatestEntry> data = adminToolsRepository.getDataDeliveryOverview(null);
            String currentBusinessId = "";
            for (CompanyLatestEntry entry : data) {
                boolean isPublicValidationTest = entry.businessId().equals(Constants.PUBLIC_VALIDATION_TEST_ID);
                String companyNameToShow = isPublicValidationTest ? getPublicValidationTestName(language) : entry.companyName();
                String businessIdToShow = isPublicValidationTest ? getPublicValidationTestName(language) : entry.businessId();

                boolean putEmptyString = false;
                if (currentBusinessId.equals(entry.businessId())) {
                    putEmptyString = true;
                } else {
                    currentBusinessId = entry.businessId();
                }

                printer.printRecord(
                    putEmptyString ? "" : businessIdToShow,
                    putEmptyString ? "" : companyNameToShow,
                    entry.context(),
                    entry.url(),
                    entry.feedName(),
                    entry.format(),
                    entry.convertedFormat(),
                    entry.status() != null ? entry.status().fieldName() : "",
                    entry.created() != null ? entry.created().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")) : "",
                    entry.publicId()
                );
            }
        } catch (IOException e) {
            logger.warn("Failed to generate CSV", e); // TODO: some entry references
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
