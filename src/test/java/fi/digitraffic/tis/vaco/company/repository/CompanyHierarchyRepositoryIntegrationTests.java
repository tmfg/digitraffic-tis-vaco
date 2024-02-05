package fi.digitraffic.tis.vaco.company.repository;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.Hierarchy;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
import fi.digitraffic.tis.vaco.company.model.ImmutableHierarchy;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class CompanyHierarchyRepositoryIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    private CompanyHierarchyService companyHierarchyService;

    @Autowired
    private CompanyHierarchyRepository companyHierarchyRepository;

    @Test
    void loadHierarchyWorks() {
        Company root = companyHierarchyService.createCompany(ImmutableCompany.of("112233-4", "Kompany Ky")).get();
        Company childA = companyHierarchyService.createCompany(ImmutableCompany.of("112233-5", "Virma Oy")).get();
        Company childB = companyHierarchyService.createCompany(ImmutableCompany.of("112233-6", "Puulaaki Oyj")).get();
        Company grandchildC = companyHierarchyService.createCompany(ImmutableCompany.of("112233-7", "Limited Ltd.")).get();

        companyHierarchyService.createPartnership(PartnershipType.AUTHORITY_PROVIDER, root, childA);
        companyHierarchyService.createPartnership(PartnershipType.AUTHORITY_PROVIDER, root, childB);
        companyHierarchyService.createPartnership(PartnershipType.AUTHORITY_PROVIDER, childB, grandchildC);

        Map<Company, Hierarchy> h = companyHierarchyRepository.findRootHierarchies();

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
}
