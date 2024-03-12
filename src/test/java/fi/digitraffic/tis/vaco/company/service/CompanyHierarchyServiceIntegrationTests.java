package fi.digitraffic.tis.vaco.company.service;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.Hierarchy;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
import fi.digitraffic.tis.vaco.company.model.ImmutableHierarchy;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class CompanyHierarchyServiceIntegrationTests extends SpringBootIntegrationTestBase {
    @Autowired
    private CompanyHierarchyService companyHierarchyService;

    @Test
    void truncatedHierarchyWorks() {
        Company root = companyHierarchyService.createCompany(ImmutableCompany.of("112233-4", "Kompany Ky")).get();
        Company childA = companyHierarchyService.createCompany(ImmutableCompany.of("112233-5", "Virma Oy")).get();
        Company childB = companyHierarchyService.createCompany(ImmutableCompany.of("112233-6", "Puulaaki Oyj")).get();
        Company grandchildC = companyHierarchyService.createCompany(ImmutableCompany.of("112233-7", "Limited Ltd.")).get();
        Company grandchildD = companyHierarchyService.createCompany(ImmutableCompany.of("112233-8", "Limited Ltd.")).get();
        Company grandGrandchild = companyHierarchyService.createCompany(ImmutableCompany.of("112235-9", "Hmm Ltd.")).get();

        companyHierarchyService.createPartnership(PartnershipType.AUTHORITY_PROVIDER, root, childA);
        companyHierarchyService.createPartnership(PartnershipType.AUTHORITY_PROVIDER, root, childB);
        companyHierarchyService.createPartnership(PartnershipType.AUTHORITY_PROVIDER, childB, grandchildC);
        companyHierarchyService.createPartnership(PartnershipType.AUTHORITY_PROVIDER, childB, grandchildD);
        companyHierarchyService.createPartnership(PartnershipType.AUTHORITY_PROVIDER, grandchildC, grandGrandchild);

        List<Hierarchy> h = companyHierarchyService.getHierarchies(childB.businessId());

        Hierarchy expected = ImmutableHierarchy.builder()
            .company(root)
            .addChildren(
                ImmutableHierarchy.builder()
                    .company(childB)
                    .addChildren(ImmutableHierarchy.builder().company(grandchildC).build(), ImmutableHierarchy.builder().company(grandchildD).build())
                    .build())
            .build();

        assertThat(h.get(0), equalTo(expected));
    }
}
