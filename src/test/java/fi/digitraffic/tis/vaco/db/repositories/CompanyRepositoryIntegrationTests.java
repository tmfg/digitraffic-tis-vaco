package fi.digitraffic.tis.vaco.db.repositories;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.Hierarchy;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
import fi.digitraffic.tis.vaco.company.model.ImmutableHierarchy;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class CompanyRepositoryIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    private CompanyHierarchyService companyHierarchyService;

    @Autowired
    private CompanyRepository companyRepository;

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
            .withPublish(false);
        Company updatedCompany = companyRepository.update(companyToUpdate.businessId(), companyToUpdate);
        assertThat(updatedCompany.name(), equalTo(companyToUpdate.name()));
        assertThat(updatedCompany.adGroupId(), equalTo(companyToUpdate.adGroupId()));
        assertThat(updatedCompany.contactEmails(), equalTo(companyToUpdate.contactEmails()));
        assertThat(updatedCompany.language(), equalTo(companyToUpdate.language()));
        assertThat(updatedCompany.publish(), equalTo(companyToUpdate.publish()));
    }
}
