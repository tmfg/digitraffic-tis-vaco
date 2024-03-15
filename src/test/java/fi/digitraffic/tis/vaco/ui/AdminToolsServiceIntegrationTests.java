package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.repository.CompanyHierarchyRepository;
import fi.digitraffic.tis.vaco.entries.EntryRepository;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.springframework.beans.factory.annotation.Autowired;

class AdminToolsServiceIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    private AdminToolsService adminToolsService;
    @Autowired
    private CompanyHierarchyRepository companyHierarchyRepository;
    @Autowired
    EntryRepository entryRepository;
    Company companyWithOnlyGtfs;
    Entry gtfsEntry;

    // Temporarily commented out
    /*@BeforeAll
    void setUp() {
        JwtAuthenticationToken token = TestObjects.jwtAuthenticationToken("Jornado");
        SecurityContextHolder.getContext().setAuthentication(token);

        entryRepository.create(TestObjects.anEntry().build());
        entryRepository.create(TestObjects.anEntry().build());
        entryRepository.create(TestObjects.anEntry().format("netex").build());
        entryRepository.create(TestObjects.anEntry().format("netex").build());
        injectGroupIdToCompany(token);

        companyWithOnlyGtfs = TestObjects.aCompany().name("Company only with gtfs").build();
        companyHierarchyRepository.create(companyWithOnlyGtfs);
        gtfsEntry = TestObjects.anEntry().businessId(companyWithOnlyGtfs.businessId()).build();
        entryRepository.create(gtfsEntry);
        injectGroupIdToCompany(token, companyWithOnlyGtfs.businessId());

        Company company3 = TestObjects.aCompany().name("Company only with netex").build();
        companyHierarchyRepository.create(company3);
        Entry e2 = TestObjects.anEntry().businessId(company3.businessId()).format("netex").build();
        entryRepository.create(e2);
        injectGroupIdToCompany(token, company3.businessId());

        Company company4 = TestObjects.aCompany().name("Company with no data at all").build();
        companyHierarchyRepository.create(company4);
        injectGroupIdToCompany(token, company4.businessId());

        Company company5 = TestObjects.aCompany().name("A company user isn't part of").build();
        companyHierarchyRepository.create(company5);
    }

    @Test
    void testGetAllLatestEntriesPerCompany() {
        List<CompanyLatestEntry> companyLatestEntries = adminToolsService.listLatestEntriesPerCompany(null);
        Optional<CompanyLatestEntry> fintrafficData = companyLatestEntries.stream()
            .filter(c -> c.businessId().equals(Constants.FINTRAFFIC_BUSINESS_ID)).findFirst();
        assertTrue(fintrafficData.isPresent());
        assertThat(fintrafficData.get().companyName(), equalTo("Fintraffic Oy"));
        assertThat(fintrafficData.get().businessId(), equalTo(Constants.FINTRAFFIC_BUSINESS_ID));
        Optional<CompanyLatestEntry> noDataCompany = companyLatestEntries.stream()
            .filter(c -> c.companyName().equals("Company with no data at all")).findFirst();
        assertTrue(noDataCompany.isPresent());
        // The logic of listLatestEntriesPerCompany related to entries accumulation will change, so not testing the entry data now
    }

    @Test
    void testGetLatestEntriesPerUserCompany() {
        Company fintraffic = companyHierarchyRepository.findByBusinessId(Constants.FINTRAFFIC_BUSINESS_ID).get();
        List<CompanyLatestEntry> companyLatestEntries = adminToolsService.listLatestEntriesPerCompany(Set.of(fintraffic));
        assertThat(companyLatestEntries.size(), equalTo(2));
        Optional<CompanyLatestEntry> fintrafficData = companyLatestEntries.stream()
            .filter(c -> c.businessId().equals(Constants.FINTRAFFIC_BUSINESS_ID)).findFirst();
        assertTrue(fintrafficData.isPresent());
        assertThat(fintrafficData.get().companyName(), equalTo(fintraffic.name()));
        assertThat(fintrafficData.get().businessId(), equalTo(fintraffic.businessId()));
        // The logic of listLatestEntriesPerCompany related to entries accumulation will change, so not testing the entry data now
    }

    @Test
    void testDataDeliveryOverviewForAdmin() {
        List<CompanyLatestEntry> data = adminToolsService.getDataDeliveryOverview(null);
        assertThat(data.size(), equalTo(6));

        List<CompanyLatestEntry> fintrafficEntries = data.stream()
            .filter(c -> c.businessId().equals(Constants.FINTRAFFIC_BUSINESS_ID)).toList();
        assertThat(fintrafficEntries.size(), equalTo(2));

        List<CompanyLatestEntry> companyWithOnlyGtfsEntries = data.stream()
            .filter(c -> c.companyName().equals(companyWithOnlyGtfs.name())).toList();
        assertThat(companyWithOnlyGtfsEntries.size(), equalTo(1));
        CompanyLatestEntry gtfsEntry = companyWithOnlyGtfsEntries.get(0);
        assertThat(gtfsEntry.companyName(), equalTo(companyWithOnlyGtfs.name()));
        assertThat(gtfsEntry.businessId(), equalTo(companyWithOnlyGtfs.businessId()));
        assertThat(gtfsEntry.url(), equalTo(gtfsEntry.url()));
        assertThat(gtfsEntry.feedName(), equalTo(gtfsEntry.feedName()));
        assertThat(gtfsEntry.status(), equalTo(Status.RECEIVED));

        List<CompanyLatestEntry> noDataCompanyEntries = data.stream()
            .filter(c -> c.companyName().equals("Company with no data at all")).toList();
        assertThat(noDataCompanyEntries.size(), equalTo(1));
    }

    @Test
    void testDataDeliveryOverviewForCompanyAdmin() {
        Company fintraffic = companyHierarchyRepository.findByBusinessId(Constants.FINTRAFFIC_BUSINESS_ID).get();
        List<CompanyLatestEntry> data = adminToolsService.getDataDeliveryOverview(Set.of(fintraffic));
        assertThat(data.size(), equalTo(2));
    }

    @Test
    void testGetCompaniesWithFormatInfos() {
        List<CompanyWithFormatSummary> companyWithFormatInfos = adminToolsService.getCompaniesWithFormatInfos();
        assertThat(companyWithFormatInfos.size(), equalTo(4));
        Optional<CompanyWithFormatSummary> fintraffic = companyWithFormatInfos.stream()
            .filter(c -> c.businessId().equals(Constants.FINTRAFFIC_BUSINESS_ID)).findFirst();
        assertTrue(fintraffic.isPresent());
        assertThat(fintraffic.get().name(), equalTo("Fintraffic Oy"));
        assertThat(fintraffic.get().businessId(), equalTo(Constants.FINTRAFFIC_BUSINESS_ID));
        assertThat(fintraffic.get().formatSummary(), containsString("GTFS"));
        assertThat(fintraffic.get().formatSummary(), containsString("NeTEx"));

        Optional<CompanyWithFormatSummary> companyWithOnlyGtfs = companyWithFormatInfos.stream()
            .filter(c -> c.name().equals("Company only with gtfs")).findFirst();
        assertTrue(companyWithOnlyGtfs.isPresent());
        assertThat(companyWithOnlyGtfs.get().formatSummary(), containsString("GTFS"));
        assertThat(companyWithOnlyGtfs.get().formatSummary(), not(containsString("NeTEx")));

        Optional<CompanyWithFormatSummary> companyWithOnlyNetex = companyWithFormatInfos.stream()
            .filter(c -> c.name().equals("Company only with netex")).findFirst();
        assertTrue(companyWithOnlyNetex.isPresent());
        assertThat(companyWithOnlyNetex.get().formatSummary(), containsString("NeTEx"));
        assertThat(companyWithOnlyNetex.get().formatSummary(), not(containsString("GTFS")));

        Optional<CompanyWithFormatSummary> companyWithNoData = companyWithFormatInfos.stream()
            .filter(c -> c.name().equals("Company with no data at all")).findFirst();
        assertTrue(companyWithNoData.isPresent());
        assertNull(companyWithNoData.get().formatSummary());

        Optional<CompanyWithFormatSummary> companyOutOfUserScope = companyWithFormatInfos.stream()
            .filter(c -> c.name().equals("A company user isn't part of")).findFirst();
        assertTrue(companyOutOfUserScope.isEmpty());
    }*/
}
