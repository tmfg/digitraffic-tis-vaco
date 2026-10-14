package fi.digitraffic.tis.vaco.db.repositories;

import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.company.dto.CompanyDeletionResult;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.Hierarchy;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
import fi.digitraffic.tis.vaco.company.model.ImmutableHierarchy;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import fi.digitraffic.tis.vaco.company.service.model.CompanyRole;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.RulesetType;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class CompanyRepositoryIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    private CompanyHierarchyService companyHierarchyService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private RulesetRepository rulesetRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void loadHierarchyWorks() {
        Company root = companyHierarchyService.createCompany(ImmutableCompany.of("312233-4", "Kompany Ky", true)).get();
        Company childA = companyHierarchyService.createCompany(ImmutableCompany.of("112233-5", "Virma Oy", true)).get();
        Company childB = companyHierarchyService.createCompany(ImmutableCompany.of("112233-6", "Puulaaki Oyj", true)).get();
        Company grandchildC = companyHierarchyService.createCompany(ImmutableCompany.of("112233-7", "Limited Ltd.", true)).get();

        companyHierarchyService.createPartnership(PartnershipType.AUTHORITY_PROVIDER, root, childA);
        companyHierarchyService.createPartnership(PartnershipType.AUTHORITY_PROVIDER, root, childB);
        companyHierarchyService.createPartnership(PartnershipType.AUTHORITY_PROVIDER, childB, grandchildC);

        Map<Company, Hierarchy> h = companyRepository.findRootHierarchies();

        Hierarchy expected = ImmutableHierarchy.builder()
            .company(root)
            .addChildren(
                ImmutableHierarchy.builder().company(childA).build(),
                ImmutableHierarchy.builder()
                    .company(childB)
                    .addChildren(ImmutableHierarchy.builder().company(grandchildC).build())
                    .build())
            .build();

        assertThat(h.get(root), equalTo(expected));
    }

    @Test
    void testEditCompany() {
        Company company = companyHierarchyService.createCompany(ImmutableCompany.of("112233-4", "Kompany Ky", true)).get();
        Company companyToUpdate = ImmutableCompany.copyOf(company)
            .withName("Some company")
            .withAdGroupId("ad")
            .withContactEmails(List.of("email"))
            .withLanguage("en")
            .withRoles(List.of(CompanyRole.AUTHORITY, CompanyRole.OPERATOR))
            .withPublish(false);
        CompanyRecord updatedCompany = companyRepository.update(companyToUpdate.businessId(), companyToUpdate);
        assertThat(updatedCompany.name(), equalTo(companyToUpdate.name()));
        assertThat(updatedCompany.adGroupId(), equalTo(companyToUpdate.adGroupId()));
        assertThat(updatedCompany.contactEmails(), equalTo(companyToUpdate.contactEmails()));
        assertThat(updatedCompany.language(), equalTo(companyToUpdate.language()));
        assertThat(updatedCompany.publish(), equalTo(companyToUpdate.publish()));
        assertThat(updatedCompany.roles(), equalTo(companyToUpdate.roles()));
    }

    @Test
    void deleteWithLinkedDataRemovesEveryKindOfLinkedRow() {
        CompanyRecord company = createCompany("Deleted");
        CompanyRecord other = createCompany("Other");
        long credentialsId = insertOwnData(company);
        linkCompanies(company, other);
        long entryWithTask = insertEntry(company, "success", null);
        jdbc.update("INSERT INTO task(entry_id, name, priority) VALUES (?, 'prepare.download', 100)", entryWithTask);
        insertEntry(company, "errors", credentialsId);

        CompanyDeletionResult result = companyRepository.deleteWithLinkedData(company.businessId());

        assertThat(result.status(), is(CompanyDeletionResult.Status.DELETED));
        assertThat(result.deleted(), equalTo(Map.of(
            "entries", 3L,
            "partnerships", 1L,
            "ruleset_grants", 2L,
            "contexts", 1L,
            "subscriptions", 2L,
            "feeds", 1L,
            "credentials", 1L)));
        assertThat(companyRepository.findByBusinessId(company.businessId()).isPresent(), is(false));
        assertThat(countEntries(company), is(0L));
        assertThat(count("SELECT count(*) FROM task WHERE entry_id = ?", entryWithTask), is(0L));
        assertThat(countContexts(company), is(0L));
        assertThat(count("SELECT count(*) FROM feed WHERE owner_id = ?", company.id()), is(0L));
        assertThat(count("SELECT count(*) FROM credentials WHERE owner_id = ?", company.id()), is(0L));
        assertThat(count("SELECT count(*) FROM subscription WHERE subscriber_id = ? OR resource_id = ?", company.id(), company.id()), is(0L));
        assertThat(count("SELECT count(*) FROM partnership WHERE partner_a_id = ? OR partner_b_id = ?", company.id(), company.id()), is(0L));
        assertThat(count("SELECT count(*) FROM ruleset_access WHERE company_id = ?", company.id()), is(0L));
    }

    @Test
    void deleteWithLinkedDataKeepsTheOtherCompanysOwnData() {
        CompanyRecord company = createCompany("Deleted");
        CompanyRecord other = createCompany("Other");
        insertOwnData(company);
        insertOwnData(other);
        insertEntry(other, "success", null);
        linkCompanies(company, other);

        companyRepository.deleteWithLinkedData(company.businessId());

        assertThat(companyRepository.findByBusinessId(other.businessId()).isPresent(), is(true));
        assertThat(countEntries(other), is(2L));
        assertThat(countContexts(other), is(1L));
        assertThat(count("SELECT count(*) FROM feed WHERE owner_id = ?", other.id()), is(1L));
        assertThat(count("SELECT count(*) FROM credentials WHERE owner_id = ?", other.id()), is(1L));
        assertThat(count("SELECT count(*) FROM ruleset_access WHERE company_id = ?", other.id()), is(2L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"received", "processing"})
    void deleteWithLinkedDataRefusesWhileAnEntryIsUnfinished(String status) {
        CompanyRecord company = createCompany("Busy");
        insertOwnData(company);
        insertEntry(company, status, null);

        CompanyDeletionResult result = companyRepository.deleteWithLinkedData(company.businessId());

        assertThat(result.status(), is(CompanyDeletionResult.Status.IN_PROGRESS));
        assertThat(companyRepository.findByBusinessId(company.businessId()).isPresent(), is(true));
        assertThat(countEntries(company), is(2L));
        assertThat(countContexts(company), is(1L));
    }

    @Test
    void deleteWithLinkedDataDeletesCompanyWithoutLinkedData() {
        CompanyRecord company = createCompany("Alone");

        CompanyDeletionResult result = companyRepository.deleteWithLinkedData(company.businessId());

        assertThat(result.status(), is(CompanyDeletionResult.Status.DELETED));
        assertThat(result.deleted(), equalTo(Map.of()));
        assertThat(companyRepository.findByBusinessId(company.businessId()).isPresent(), is(false));
    }

    @Test
    void deleteWithLinkedDataReportsMissingCompany() {
        assertThat(companyRepository.deleteWithLinkedData("0000000-0").status(), is(CompanyDeletionResult.Status.NOT_FOUND));
    }

    @Test
    void countReferencesIsEmptyForMissingCompany() {
        assertThat(companyRepository.countReferences("0000000-0").isPresent(), is(false));
    }

    @Test
    void serviceRefusesProtectedCompanies() {
        assertThat(companyHierarchyService.deleteCompany(Constants.FINTRAFFIC_BUSINESS_ID).status(), is(CompanyDeletionResult.Status.PROTECTED));
        assertThat(companyHierarchyService.deleteCompany(Constants.PUBLIC_VALIDATION_TEST_ID).status(), is(CompanyDeletionResult.Status.PROTECTED));
        assertThat(companyHierarchyService.deleteCompany(Constants.TRAFICOM_BUSINESS_ID).status(), is(CompanyDeletionResult.Status.PROTECTED));
        assertThat(companyHierarchyService.findByBusinessId(Constants.FINTRAFFIC_BUSINESS_ID).isPresent(), is(true));
    }

    @Test
    void serviceDeleteRemovesCompanyFromHierarchyLookups() {
        CompanyRecord company = createCompany("Hierarchy gone");

        assertThat(companyHierarchyService.deleteCompany(company.businessId()).status(), is(CompanyDeletionResult.Status.DELETED));

        assertThat(companyHierarchyService.findByBusinessId(company.businessId()).isPresent(), is(false));
        assertThat(companyHierarchyService.getHierarchiesContainingCompany(company.businessId()).isEmpty(), is(true));
    }

    /**
     * Gives the company one finished entry, one context, one feed, one set of credentials, and one ruleset with a grant.
     * Returns the credentials id.
     */
    private long insertOwnData(CompanyRecord company) {
        insertEntry(company, "success", null);
        jdbc.update("INSERT INTO context(context, company_id) VALUES ('ctx', ?)", company.id());
        jdbc.update("INSERT INTO feed(owner_id, format, uri) VALUES (?, 'gtfs', 'http://x')", company.id());
        rulesetRepository.createRuleset(
            company,
            ImmutableRuleset.of("OWN_" + company.businessId(), "Own", Category.SPECIFIC, RulesetType.VALIDATION_SYNTAX, TransitDataFormat.GTFS));
        return jdbc.queryForObject(
            "INSERT INTO credentials(owner_id, name, type) VALUES (?, 'n', 'HTTP Basic') RETURNING id",
            Long.class,
            company.id());
    }

    /**
     * Links the company to the other one with a partnership, a subscription in each direction and a grant to the
     * other company's ruleset.
     */
    private void linkCompanies(CompanyRecord company, CompanyRecord other) {
        companyHierarchyService.createPartnership(PartnershipType.AUTHORITY_PROVIDER, toCompany(other), toCompany(company));
        jdbc.update("INSERT INTO subscription(type, subscriber_id, resource_id) VALUES ('webhook', ?, ?)", company.id(), other.id());
        jdbc.update("INSERT INTO subscription(type, subscriber_id, resource_id) VALUES ('webhook', ?, ?)", other.id(), company.id());
        var ruleset = rulesetRepository.createRuleset(
            other,
            ImmutableRuleset.of("SHARED_" + other.businessId(), "Shared", Category.SPECIFIC, RulesetType.VALIDATION_SYNTAX, TransitDataFormat.GTFS));
        jdbc.update("INSERT INTO ruleset_access(company_id, ruleset_id) VALUES (?, ?)", company.id(), ruleset.id());
    }

    private long insertEntry(CompanyRecord company, String status, Long credentialsId) {
        return jdbc.queryForObject(
            "INSERT INTO entry(format, url, business_id, status, credentials_id) VALUES ('gtfs', 'http://x', ?, ?::status, ?) RETURNING id",
            Long.class,
            company.businessId(), status, credentialsId);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long countEntries(CompanyRecord company) {
        return count("SELECT count(*) FROM entry WHERE business_id = ?", company.businessId());
    }

    private long countContexts(CompanyRecord company) {
        return count("SELECT count(*) FROM context WHERE company_id = ?", company.id());
    }

    private CompanyRecord createCompany(String name) {
        String businessId = String.format("%07d-%d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999), 1);
        return companyRepository.create(ImmutableCompany.of(businessId, name, true)).get();
    }

    private static Company toCompany(CompanyRecord companyRecord) {
        return ImmutableCompany.of(companyRecord.businessId(), companyRecord.name(), true);
    }
}
