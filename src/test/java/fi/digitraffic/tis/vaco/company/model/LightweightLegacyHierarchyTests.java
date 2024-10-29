package fi.digitraffic.tis.vaco.company.model;

import fi.digitraffic.tis.vaco.company.service.model.ImmutableLightweightHierarchy;
import fi.digitraffic.tis.vaco.company.service.model.LightweightHierarchy;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class LightweightLegacyHierarchyTests {

    @Test
    void canLookupChildren() {
        Company root = ImmutableCompany.of("112233-4", "Kompany Ky", true);
        Company childA = ImmutableCompany.of("112233-5", "Virma Oy", true);
        Company childB = ImmutableCompany.of("112233-6", "Puulaaki Oyj", true);
        Company grandchildC = ImmutableCompany.of("112233-7", "Limited Ltd.", true);

        LightweightHierarchy h = ImmutableLightweightHierarchy.of(
            root.businessId(),
            Set.of(
                ImmutableLightweightHierarchy.of(
                    childA.businessId(),
                    Set.of()),
                ImmutableLightweightHierarchy.of(
                    childB.businessId(),
                    Set.of(ImmutableLightweightHierarchy.of(grandchildC.businessId(),
                        Set.of()))
            )));

        assertThat(h.findNode(root.businessId()).get().hasChildWithBusinessId(childA.businessId()), equalTo(true));
        assertThat(h.findNode(root.businessId()).get().hasChildWithBusinessId(childB.businessId()), equalTo(true));
        assertThat(h.findNode(root.businessId()).get().hasChildWithBusinessId(grandchildC.businessId()), equalTo(true));
        assertThat(h.findNode(childA.businessId()).get().hasChildWithBusinessId(grandchildC.businessId()), equalTo(false));
        assertThat(h.findNode(childB.businessId()).get().hasChildWithBusinessId(grandchildC.businessId()), equalTo(true));

        assertThat(h.findNode("huuhaa-ytunnus").isEmpty(), equalTo(true));
    }

    @Test
    void canConvertHierarchyToLightweightHierarchy() {
        Company root = ImmutableCompany.of("123456-7", "Pelit ja Vehkeet Oy", true);
        Company child = ImmutableCompany.of("101010-1", "Kainuun Keksi ja Kalarehu Oy", true);
        Company grandhild = ImmutableCompany.of("1717171-7", "Arvat ja ja Luulot Oy", true);

        LegacyHierarchy fullLegacyHierarchy = ImmutableLegacyHierarchy.builder()
            .company(root)
            .addChildren(ImmutableLegacyHierarchy.builder()
                .company(child)
                .addChildren(ImmutableLegacyHierarchy.builder()
                    .company(grandhild)
                    .build())
                .build())
            .build();
        LightweightHierarchy lightweightHierarchy = LightweightHierarchy.from(fullLegacyHierarchy);

        assertThat(lightweightHierarchy, equalTo(ImmutableLightweightHierarchy.builder()
            .businessId(root.businessId())
            .addChildren(ImmutableLightweightHierarchy.builder()
                .businessId(child.businessId())
                .addChildren(ImmutableLightweightHierarchy.builder()
                    .businessId(grandhild.businessId())
                    .build())
                .build())
            .build()));
    }

    @Test
    void canConvertLightweightHierarchyToHierarchy() {
        Company root = ImmutableCompany.of("123456-7", "Pelit ja Vehkeet Oy", true);
        Company child = ImmutableCompany.of("101010-1", "Kainuun Keksi ja Kalarehu Oy", true);
        Company grandchild = ImmutableCompany.of("1717171-7", "Arvat ja ja Luulot Oy", true);

        LightweightHierarchy lightweightHierarchy = ImmutableLightweightHierarchy.builder()
            .businessId(root.businessId())
            .addChildren(ImmutableLightweightHierarchy.builder()
                .businessId(child.businessId())
                .addChildren(ImmutableLightweightHierarchy.builder()
                    .businessId(grandchild.businessId())
                    .build())
                .build())
            .build();

        Map<String, Company> companies = Map.of(
            root.businessId(), root,
            child.businessId(), child,
            grandchild.businessId(), grandchild
        );

        // use this to get all business ids for the hierarchy which you can then use to load matching Companies from
        // database
        Set<String> businessIds = lightweightHierarchy.collectContainedBusinessIds();
        assertThat(businessIds, equalTo(companies.keySet()));

        LegacyHierarchy fullLegacyHierarchy = lightweightHierarchy.toHierarchy(companies);
        assertThat(fullLegacyHierarchy, equalTo(ImmutableLegacyHierarchy.builder()
            .company(root)
            .addChildren(ImmutableLegacyHierarchy.builder()
                .company(child)
                .addChildren(ImmutableLegacyHierarchy.builder()
                    .company(grandchild)
                    .build())
                .build())
            .build()));
    }


}
