package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.ui.model.CompanyLatestEntry;
import fi.digitraffic.tis.vaco.ui.model.CompanyWithFormatSummary;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    private EntryRepository entryRepository;

    private Company companyWithMultipleFeeds;
    private Company companyWithOnlyGtfs;
    private Company companyWithOnlyNetex;
    private Company noDataCompany;

    @BeforeEach
    void setUp() {
        companyWithMultipleFeeds = createCompany("Company with multiple feeds", "AdminToolsService-1234");
        createEntries(
            testEntry("Some gtfs url", companyWithMultipleFeeds.businessId()),
            testEntry("Some gtfs url", companyWithMultipleFeeds.businessId()),
            testEntry("Some other gtfs url", companyWithMultipleFeeds.businessId()),
            testEntry("Some netex url", companyWithMultipleFeeds.businessId(), "netex"),
            testEntry("Some netex url", companyWithMultipleFeeds.businessId(), "netex"),
            testEntry("Another netex url", companyWithMultipleFeeds.businessId(), "netex"));

        companyWithOnlyGtfs = createCompany("Company only with gtfs", "AdminToolsService-2345");
        createEntries(testEntry("URL", companyWithOnlyGtfs.businessId()));

        companyWithOnlyNetex = createCompany("Company only with netex", "AdminToolsService-3456");
        createEntries(testEntry("URL2", companyWithOnlyNetex.businessId(), "netex"));

        noDataCompany = createCompany("Company with no data at all", "AdminToolsService-4567");
    }

    private Company createCompany(String name, String businessId) {
        Company c = TestObjects.aCompany()
            .name(name)
            .businessId(businessId)
            .build();
        return companyHierarchyService.createCompany(c)
            .or(() -> companyHierarchyService.findByBusinessId(businessId))
            .get();
    }

    private void createEntries(Entry... entries) {
        for (Entry entry : entries) {
            entryRepository.create(entry, Optional.empty(), Optional.empty()).map(created -> {
                entryRepository.markStatus(created, Status.SUCCESS);
                return created;
            }).get();
        }
    }

    private static @NotNull ImmutableEntry testEntry(String url, String businessId) {
        return TestObjects.anEntry().url(url)
            .businessId(businessId)
            .build();
    }

    private static @NotNull ImmutableEntry testEntry(String url, String businessId, String format) {
        return TestObjects.anEntry().url(url)
            .businessId(businessId)
            .format(format)
            .build();
    }

    @Test
    void testDataDeliveryOverviewForSingleCompanyAdmin() {
        List<CompanyLatestEntry> companyLatestEntries = adminToolsService.getDataDeliveryOverview(Set.of(companyWithMultipleFeeds));

        assertThat(companyLatestEntries.size(), equalTo(4));
        Optional<CompanyLatestEntry> nonCompanyData = companyLatestEntries.stream()
            .filter(c -> !c.businessId().equals("AdminToolsService-1234")).findFirst();
        Assertions.assertTrue(nonCompanyData.isEmpty());

        CompanyLatestEntry latestEntry = companyLatestEntries.get(0);
        assertThat(latestEntry.companyName(), equalTo(companyWithMultipleFeeds.name()));
        assertThat(latestEntry.businessId(), equalTo("AdminToolsService-1234"));
        assertThat(latestEntry.format().toLowerCase(), equalTo("netex"));

        CompanyLatestEntry earliestEntry = companyLatestEntries.get(companyLatestEntries.size() - 1);
        assertThat(earliestEntry.companyName(), equalTo(companyWithMultipleFeeds.name()));
        assertThat(earliestEntry.businessId(), equalTo("AdminToolsService-1234"));
        assertThat(earliestEntry.format().toLowerCase(), equalTo("gtfs"));

        List<String> uniqueUrls = companyLatestEntries.stream().map(CompanyLatestEntry::url).distinct().toList();
        assertThat(uniqueUrls.size(), equalTo(4));
    }

    @Test
    void testDataDeliveryOverviewForSupremeAdmin() {
        List<CompanyLatestEntry> data = adminToolsService.getDataDeliveryOverview(null);

        List<CompanyLatestEntry> companyWithMultipleFeedsData = data.stream()
            .filter(c -> c.businessId().equals("AdminToolsService-1234")).toList();
        assertThat(companyWithMultipleFeedsData.size(), equalTo(4));

        List<CompanyLatestEntry> companyWithOnlyGtfsEntries = data.stream()
            .filter(c -> c.companyName().equals(companyWithOnlyGtfs.name())).toList();
        assertThat(companyWithOnlyGtfsEntries.size(), equalTo(1));
        CompanyLatestEntry gtfsEntry = companyWithOnlyGtfsEntries.get(0);
        assertThat(gtfsEntry.companyName(), equalTo(companyWithOnlyGtfs.name()));
        assertThat(gtfsEntry.businessId(), equalTo(companyWithOnlyGtfs.businessId()));
        assertThat(gtfsEntry.url(), equalTo(gtfsEntry.url()));
        assertThat(gtfsEntry.feedName(), equalTo(gtfsEntry.feedName()));
        assertThat(gtfsEntry.status(), equalTo(Status.SUCCESS));

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
            .filter(c -> c.businessId().equals("AdminToolsService-1234")).findFirst();
        Assertions.assertTrue(companyWithMultipleFeedsSummary.isPresent());
        assertThat(companyWithMultipleFeedsSummary.get().name(), equalTo(companyWithMultipleFeeds.name()));
        assertThat(companyWithMultipleFeedsSummary.get().businessId(), equalTo("AdminToolsService-1234"));
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
        String oid = "A company admin";
        JwtAuthenticationToken token = TestObjects.jwtCompanyAdminAuthenticationToken(oid);
        SecurityContextHolder.getContext().setAuthentication(token);
        // inject access info
        injectAuthOverrides(oid, asFintrafficIdGroup(companyWithMultipleFeeds));

        List<CompanyWithFormatSummary> companyWithFormatInfos = adminToolsService.getCompaniesWithFormatInfos();
        assertThat(companyWithFormatInfos.size(), equalTo(1));
        CompanyWithFormatSummary companyWithMultipleFeedsSummary = companyWithFormatInfos.get(0);
        assertThat(companyWithMultipleFeedsSummary.name(), equalTo(companyWithMultipleFeeds.name()));
        assertThat(companyWithMultipleFeedsSummary.businessId(), equalTo(companyWithMultipleFeeds.businessId()));
        assertThat(companyWithMultipleFeedsSummary.formatSummary(), containsString("GTFS"));
        assertThat(companyWithMultipleFeedsSummary.formatSummary(), containsString("NeTEx"));
    }
}
