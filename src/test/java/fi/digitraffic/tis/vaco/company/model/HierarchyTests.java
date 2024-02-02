package fi.digitraffic.tis.vaco.company.model;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class HierarchyTests {

    @Test
    void canLookupChildren() {
        Company root = ImmutableCompany.of("112233-4", "Kompany Ky");
        Company childA = ImmutableCompany.of("112233-5", "Virma Oy");
        Company childB = ImmutableCompany.of("112233-6", "Puulaaki Oyj");
        Company grandchildC = ImmutableCompany.of("112233-7", "Limited Ltd.");

        Hierarchy h = ImmutableHierarchy.builder()
            .company(root)
            .addChildren(
                ImmutableHierarchy.builder().company(childA).build(),
                ImmutableHierarchy.builder()
                    .company(childB)
                    .addChildren(ImmutableHierarchy.builder().company(grandchildC).build())
                    .build())
            .build();

        assertThat(h.findNode(root.businessId()).get().hasChildWithBusinessId(childA.businessId()), equalTo(true));
        assertThat(h.findNode(root.businessId()).get().hasChildWithBusinessId(childB.businessId()), equalTo(true));
        assertThat(h.findNode(root.businessId()).get().hasChildWithBusinessId(grandchildC.businessId()), equalTo(true));
        assertThat(h.findNode(childA.businessId()).get().hasChildWithBusinessId(grandchildC.businessId()), equalTo(false));
        assertThat(h.findNode(childB.businessId()).get().hasChildWithBusinessId(grandchildC.businessId()), equalTo(true));

        assertThat(h.findNode("huuhaa-ytunnus").isEmpty(), equalTo(true));
    }
}
