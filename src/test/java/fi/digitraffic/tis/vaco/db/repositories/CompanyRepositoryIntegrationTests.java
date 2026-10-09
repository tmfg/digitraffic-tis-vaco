package fi.digitraffic.tis.vaco.db.repositories;

import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.Hierarchy;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
import fi.digitraffic.tis.vaco.company.model.ImmutableHierarchy;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import fi.digitraffic.tis.vaco.company.service.model.CompanyDeletionResult;
import fi.digitraffic.tis.vaco.company.service.model.CompanyRole;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.RulesetType;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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

    static Stream<Arguments> referencingRows() {
        return Stream.of(
            Arguments.of("entries", "INSERT INTO entry(format, url, business_id) VALUES ('gtfs', 'http://x', (SELECT business_id FROM company WHERE id = ?))"),
            Arguments.of("contexts", "INSERT INTO context(context, company_id) VALUES ('ctx', ?)"),
            Arguments.of("feeds", "INSERT INTO feed(owner_id, format, uri) VALUES (?, 'gtfs', 'http://x')"),
            Arguments.of("credentials", "INSERT INTO credentials(owner_id, name, type) VALUES (?, 'n', 'HTTP Basic')"),
            Arguments.of("subscriptions", "INSERT INTO subscription(type, subscriber_id, resource_id) VALUES ('webhook', ?, ?)"));
    }

    @ParameterizedTest
    @MethodSource("referencingRows")
    void deleteIfUnreferencedRefusesWhenLinkedDataExists(String kind, String insertSql) {
        CompanyRecord company = createCompany("Linked " + kind);
        jdbc.update(insertSql, insertSql.contains("subscriber_id") ? new Object[] {company.id(), company.id()} : new Object[] {company.id()});

        CompanyDeletionResult result = companyRepository.deleteIfUnreferenced(company.businessId());

        assertThat(result.status(), is(CompanyDeletionResult.Status.REFERENCED));
        assertThat(result.references(), equalTo(Map.of(kind, 1L)));
        assertThat(companyRepository.findByBusinessId(company.businessId()).isPresent(), is(true));
    }

    @Test
    void deleteIfUnreferencedRefusesWhenPartnershipExists() {
        CompanyRecord company = createCompany("Partner");
        CompanyRecord other = createCompany("Other partner");
        companyHierarchyService.createPartnership(PartnershipType.AUTHORITY_PROVIDER, toCompany(company), toCompany(other));

        CompanyDeletionResult result = companyRepository.deleteIfUnreferenced(company.businessId());

        assertThat(result.status(), is(CompanyDeletionResult.Status.REFERENCED));
        assertThat(result.references(), equalTo(Map.of("partnerships", 1L)));
    }

    @Test
    void deleteIfUnreferencedRefusesWhenRulesetGrantExists() {
        CompanyRecord owner = createCompany("Ruleset owner");
        CompanyRecord grantee = createCompany("Ruleset grantee");
        var ruleset = rulesetRepository.createRuleset(
            owner,
            ImmutableRuleset.of("GRANT_GUARD_" + owner.businessId(), "Grant guard", Category.SPECIFIC, RulesetType.VALIDATION_SYNTAX, TransitDataFormat.GTFS));
        jdbc.update("INSERT INTO ruleset_access(company_id, ruleset_id) VALUES (?, ?)", grantee.id(), ruleset.id());

        CompanyDeletionResult result = companyRepository.deleteIfUnreferenced(grantee.businessId());

        assertThat(result.status(), is(CompanyDeletionResult.Status.REFERENCED));
        assertThat(result.references(), equalTo(Map.of("ruleset_grants", 1L)));
    }

    @Test
    void deleteIfUnreferencedKeepsEntriesThatWouldCascade() {
        CompanyRecord company = createCompany("With entry");
        jdbc.update("INSERT INTO entry(format, url, business_id) VALUES ('gtfs', 'http://x', ?)", company.businessId());

        companyRepository.deleteIfUnreferenced(company.businessId());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM entry WHERE business_id = ?", Long.class, company.businessId()), is(1L));
    }

    @Test
    void deleteIfUnreferencedDeletesCompanyWithoutLinkedData() {
        CompanyRecord company = createCompany("Alone");

        CompanyDeletionResult result = companyRepository.deleteIfUnreferenced(company.businessId());

        assertThat(result.status(), is(CompanyDeletionResult.Status.DELETED));
        assertThat(companyRepository.findByBusinessId(company.businessId()).isPresent(), is(false));
    }

    @Test
    void deleteIfUnreferencedReportsMissingCompany() {
        assertThat(companyRepository.deleteIfUnreferenced("0000000-0").status(), is(CompanyDeletionResult.Status.NOT_FOUND));
    }

    @Test
    void serviceRefusesProtectedCompanies() {
        assertThat(companyHierarchyService.deleteCompany(Constants.FINTRAFFIC_BUSINESS_ID).status(), is(CompanyDeletionResult.Status.PROTECTED));
        assertThat(companyHierarchyService.deleteCompany(Constants.PUBLIC_VALIDATION_TEST_ID).status(), is(CompanyDeletionResult.Status.PROTECTED));
        assertThat(companyHierarchyService.findByBusinessId(Constants.FINTRAFFIC_BUSINESS_ID).isPresent(), is(true));
    }

    @Test
    void serviceDeleteRemovesCompanyFromHierarchyLookups() {
        CompanyRecord company = createCompany("Hierarchy gone");

        assertThat(companyHierarchyService.deleteCompany(company.businessId()).status(), is(CompanyDeletionResult.Status.DELETED));

        assertThat(companyHierarchyService.findByBusinessId(company.businessId()).isPresent(), is(false));
        assertThat(companyHierarchyService.getHierarchiesContainingCompany(company.businessId()).isEmpty(), is(true));
    }

    private CompanyRecord createCompany(String name) {
        String businessId = String.format("%07d-%d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999), 1);
        return companyRepository.create(ImmutableCompany.of(businessId, name, true)).get();
    }

    private static Company toCompany(CompanyRecord record) {
        return ImmutableCompany.of(record.businessId(), record.name(), true);
    }
}
