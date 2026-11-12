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
import java.util.regex.Pattern;
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

    /**
     * Writes the data delivery overview as CSV.
     *
     * @param outputStream Target of the CSV
     * @param language Language of the headers and of the public test company name
     * @param maskUrls True hides the query string, the fragment and the user information of each source URL
     */
    public void exportDataDeliveryToCsv(OutputStream outputStream, String language, boolean maskUrls) {
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
                    putEmptyString ? "" : neutralizeFormula(businessIdToShow),
                    putEmptyString ? "" : neutralizeFormula(companyNameToShow),
                    neutralizeFormula(entry.context()),
                    neutralizeFormula(maskUrls ? SourceUrls.forVisitor(entry.url()) : entry.url()),
                    neutralizeFormula(entry.feedName()),
                    neutralizeFormula(entry.format()),
                    neutralizeFormula(entry.convertedFormat()),
                    entry.status() != null ? entry.status().fieldName() : "",
                    entry.created() != null ? entry.created().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")) : "",
                    neutralizeFormula(entry.publicId())
                );
            }
        } catch (IOException e) {
            logger.warn("Failed to generate CSV", e); // TODO: some entry references
        }
    }

    /**
     * A bare "-" is the placeholder for a missing value, and a plain number is not an expression.
     * Both stay unchanged. Anything else that starts with a formula character is still neutralized.
     */
    private static final Pattern PLAIN_VALUE = Pattern.compile("-|-?\\d+(\\.\\d+)?");

    /** a cell that starts with one of these characters as a formula. A prefix of a
     * single quote makes it plain text. Anonymous callers can name an entry, so every free-text cell needs this.
     */
    private static String neutralizeFormula(@Nullable String cell) {
        if (cell == null || cell.isEmpty() || PLAIN_VALUE.matcher(cell).matches()) {
            return cell;
        }
        return switch (cell.charAt(0)) {
            case '=', '+', '-', '@', '\t', '\r' -> "'" + cell;
            default -> cell;
        };
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
