package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.repository.CompanyHierarchyRepository;
import fi.digitraffic.tis.vaco.entries.EntryRepository;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.ui.model.CompanyLatestEntry;
import fi.digitraffic.tis.vaco.ui.model.CompanyWithFormatSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminToolsServiceIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    private AdminToolsService adminToolsService;
    @Autowired
    private CompanyHierarchyRepository companyHierarchyRepository;
    @Autowired
    EntryRepository entryRepository;

    @BeforeEach
    void setUp() {
        JwtAuthenticationToken token = TestObjects.jwtAuthenticationToken("Jornado");
        SecurityContextHolder.getContext().setAuthentication(token);

        entryRepository.create(TestObjects.anEntry().build());
        entryRepository.create(TestObjects.anEntry().build());
        entryRepository.create(TestObjects.anEntry().format("netex").build());
        entryRepository.create(TestObjects.anEntry().format("netex").build());
        injectGroupIdToCompany(token);

        Company company2 = TestObjects.aCompany().name("Company only with gtfs").build();
        companyHierarchyRepository.create(company2);
        Entry e1 = TestObjects.anEntry().businessId(company2.businessId()).build();
        entryRepository.create(e1);
        injectGroupIdToCompany(token, company2.businessId());

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
    }
}
