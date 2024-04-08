package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.entries.EntryRepository;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.ui.model.CompanyLatestEntry;
import fi.digitraffic.tis.vaco.ui.model.CompanyWithFormatSummary;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

class AdminToolsServiceIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    private AdminToolsService adminToolsService;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    EntryRepository entryRepository;
    final String companyWithMultipleFeedsBusinessId = "AdminToolsService-1234";
    Company companyWithMultipleFeeds;
    final String companyWithOnlyGtfsBusinessId = "AdminToolsService-2345";
    Company companyWithOnlyGtfs;
    final String companyWithOnlyNetexBusinessId = "AdminToolsService-3456";
    Company companyWithOnlyNetex;
    final String noDataCompanyBusinessId = "AdminToolsService-4567";
    Company noDataCompany;
    Entry gtfsEntry;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @BeforeEach
    void setUp() {
        companyWithMultipleFeeds = TestObjects.aCompany()
            .name("Company with multiple feeds").businessId(companyWithMultipleFeedsBusinessId).build();
        if (companyRepository.findByBusinessId(companyWithMultipleFeedsBusinessId).isEmpty()) {
            companyRepository.create(companyWithMultipleFeeds);
        }
        entryRepository.create(Optional.empty(),
            TestObjects.anEntry().url("Some gtfs url").businessId(companyWithMultipleFeedsBusinessId).build());
        entryRepository.create(Optional.empty(),
            TestObjects.anEntry().url("Some gtfs url").businessId(companyWithMultipleFeedsBusinessId).build());
        entryRepository.create(Optional.empty(),
            TestObjects.anEntry().url("Some other gtfs url").businessId(companyWithMultipleFeedsBusinessId).build());
        entryRepository.create(Optional.empty(),
            TestObjects.anEntry().url("Some netex url").businessId(companyWithMultipleFeedsBusinessId).format("netex").build());
        entryRepository.create(Optional.empty(),
            TestObjects.anEntry().url("Some netex url").businessId(companyWithMultipleFeedsBusinessId).format("netex").build());
        entryRepository.create(Optional.empty(),
            TestObjects.anEntry().url("Another netex url").businessId(companyWithMultipleFeedsBusinessId).format("netex").build());

        companyWithOnlyGtfs = TestObjects.aCompany().name("Company only with gtfs").businessId(companyWithOnlyGtfsBusinessId).build();
        if (companyRepository.findByBusinessId(companyWithOnlyGtfs.businessId()).isEmpty()) {
            companyRepository.create(companyWithOnlyGtfs);
        }
        gtfsEntry = TestObjects.anEntry().url("URL").businessId(companyWithOnlyGtfs.businessId()).build();
        entryRepository.create(Optional.empty(), gtfsEntry);

        companyWithOnlyNetex = TestObjects.aCompany().name("Company only with netex").businessId(companyWithOnlyNetexBusinessId).build();
        if (companyRepository.findByBusinessId(companyWithOnlyNetex.businessId()).isEmpty()) {
            companyRepository.create(companyWithOnlyNetex);
        }
        Entry netexEntry = TestObjects.anEntry().url("URL2").businessId(companyWithOnlyNetex.businessId()).format("netex").build();
        entryRepository.create(Optional.empty(), netexEntry);

        noDataCompany = TestObjects.aCompany().name("Company with no data at all").businessId(noDataCompanyBusinessId).build();
        if (companyRepository.findByBusinessId(noDataCompany.businessId()).isEmpty()) {
            companyRepository.create(noDataCompany);
        }
    }

    @Test
    void testDataDeliveryOverviewForSingleCompanyAdmin() {
        List<CompanyLatestEntry> companyLatestEntries = adminToolsService.getDataDeliveryOverview(Set.of(companyWithMultipleFeeds));
        companyLatestEntries.forEach(c -> {
            logger.info("DataDeliveryOverviewForSingleCompanyAdmin: " + c);
        });
        assertThat(companyLatestEntries.size(), equalTo(4));
        Optional<CompanyLatestEntry> nonCompanyData = companyLatestEntries.stream()
            .filter(c -> !c.businessId().equals(companyWithMultipleFeedsBusinessId)).findFirst();
        Assertions.assertTrue(nonCompanyData.isEmpty());

        CompanyLatestEntry latestEntry = companyLatestEntries.get(0);
        assertThat(latestEntry.companyName(), equalTo(companyWithMultipleFeeds.name()));
        assertThat(latestEntry.businessId(), equalTo(companyWithMultipleFeedsBusinessId));
        assertThat(latestEntry.format().toLowerCase(), equalTo("netex"));

        CompanyLatestEntry earliestEntry = companyLatestEntries.get(companyLatestEntries.size() - 1);
        assertThat(earliestEntry.companyName(), equalTo(companyWithMultipleFeeds.name()));
        assertThat(earliestEntry.businessId(), equalTo(companyWithMultipleFeedsBusinessId));
        assertThat(earliestEntry.format().toLowerCase(), equalTo("gtfs"));

        List<String> uniqueUrls = companyLatestEntries.stream().map(CompanyLatestEntry::url).distinct().toList();
        assertThat(uniqueUrls.size(), equalTo(4));
    }

    @Test
    void testDataDeliveryOverviewForSupremeAdmin() {
        List<CompanyLatestEntry> data = adminToolsService.getDataDeliveryOverview(null);
        data.forEach(c -> {
            logger.info("DataDeliveryOverviewForSupremeAdmin: " + c);
        });

        List<CompanyLatestEntry> companyWithMultipleFeedsData = data.stream()
            .filter(c -> c.businessId().equals(companyWithMultipleFeedsBusinessId)).toList();
        assertThat(companyWithMultipleFeedsData.size(), equalTo(4));

        List<CompanyLatestEntry> companyWithOnlyGtfsEntries = data.stream()
            .filter(c -> c.companyName().equals(companyWithOnlyGtfs.name())).toList();
        assertThat(companyWithOnlyGtfsEntries.size(), equalTo(1));
        CompanyLatestEntry gtfsEntry = companyWithOnlyGtfsEntries.get(0);
        assertThat(gtfsEntry.companyName(), equalTo(companyWithOnlyGtfs.name()));
        assertThat(gtfsEntry.businessId(), equalTo(companyWithOnlyGtfs.businessId()));
        assertThat(gtfsEntry.url(), equalTo(gtfsEntry.url()));
        assertThat(gtfsEntry.feedName(), equalTo(gtfsEntry.feedName()));
        assertThat(gtfsEntry.status(), equalTo(Status.RECEIVED));

        List<CompanyLatestEntry> companyWithOnlyNetexEntries = data.stream()
            .filter(c -> c.companyName().equals(companyWithOnlyNetex.name())).toList();
        assertThat(companyWithOnlyNetexEntries.size(), equalTo(1));

        List<CompanyLatestEntry> noDataCompanyEntries = data.stream()
            .filter(c -> c.companyName().equals(noDataCompany.name())).toList();
        assertThat(noDataCompanyEntries.size(), equalTo(1));
    }

    @Test
    void testGetCompaniesWithFormatInfosForSupremeAdmin() {
        JwtAuthenticationToken token = TestObjects.jwtAdminAuthenticationToken("A supreme admin");
        SecurityContextHolder.getContext().setAuthentication(token);

        List<CompanyWithFormatSummary> companyWithFormatInfos = adminToolsService.getCompaniesWithFormatInfos();

        Optional<CompanyWithFormatSummary> companyWithMultipleFeedsSummary = companyWithFormatInfos.stream()
            .filter(c -> c.businessId().equals(companyWithMultipleFeedsBusinessId)).findFirst();
        Assertions.assertTrue(companyWithMultipleFeedsSummary.isPresent());
        assertThat(companyWithMultipleFeedsSummary.get().name(), equalTo(companyWithMultipleFeeds.name()));
        assertThat(companyWithMultipleFeedsSummary.get().businessId(), equalTo(companyWithMultipleFeedsBusinessId));
        assertThat(companyWithMultipleFeedsSummary.get().formatSummary(), containsString("GTFS"));
        assertThat(companyWithMultipleFeedsSummary.get().formatSummary(), containsString("NeTEx"));

        Optional<CompanyWithFormatSummary> companyWithOnlyGtfsSummary = companyWithFormatInfos.stream()
            .filter(c -> c.name().equals(companyWithOnlyGtfs.name())).findFirst();
        Assertions.assertTrue(companyWithOnlyGtfsSummary.isPresent());
        assertThat(companyWithOnlyGtfsSummary.get().formatSummary(), containsString("GTFS"));
        assertThat(companyWithOnlyGtfsSummary.get().formatSummary(), Matchers.not(containsString("NeTEx")));

        Optional<CompanyWithFormatSummary> companyWithOnlyNetexSummary = companyWithFormatInfos.stream()
            .filter(c -> c.name().equals(companyWithOnlyNetex.name())).findFirst();
        Assertions.assertTrue(companyWithOnlyNetexSummary.isPresent());
        assertThat(companyWithOnlyNetexSummary.get().formatSummary(), containsString("NeTEx"));
        assertThat(companyWithOnlyNetexSummary.get().formatSummary(), Matchers.not(containsString("GTFS")));

        Optional<CompanyWithFormatSummary> companyWithNoDataSummary = companyWithFormatInfos.stream()
            .filter(c -> c.name().equals(noDataCompany.name())).findFirst();
        Assertions.assertTrue(companyWithNoDataSummary.isPresent());
        Assertions.assertNull(companyWithNoDataSummary.get().formatSummary());
    }

    @Test
    void testGetCompaniesWithFormatInfosForCompanyAdmin() {
        JwtAuthenticationToken token = TestObjects.jwtCompanyAdminAuthenticationToken("A company admin");
        SecurityContextHolder.getContext().setAuthentication(token);
        injectGroupIdToCompany(token, companyWithMultipleFeedsBusinessId);

        List<CompanyWithFormatSummary> companyWithFormatInfos = adminToolsService.getCompaniesWithFormatInfos();
        assertThat(companyWithFormatInfos.size(), equalTo(1));
        CompanyWithFormatSummary companyWithMultipleFeedsSummary = companyWithFormatInfos.get(0);
        assertThat(companyWithMultipleFeedsSummary.name(), equalTo(companyWithMultipleFeeds.name()));
        assertThat(companyWithMultipleFeedsSummary.businessId(), equalTo(companyWithMultipleFeedsBusinessId));
        assertThat(companyWithMultipleFeedsSummary.formatSummary(), containsString("GTFS"));
        assertThat(companyWithMultipleFeedsSummary.formatSummary(), containsString("NeTEx"));
    }
}
