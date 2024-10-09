package fi.digitraffic.tis.vaco.exports;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;
import org.rutebanken.netex.model.ContactStructure;
import org.rutebanken.netex.model.GeneralOrganisation;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.ResourceFrame;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

class ExportsServiceTests extends SpringBootIntegrationTestBase {

    @Autowired
    private ExportsService exportsService;

    @Test
    void exportsFullCompanyDetails() {
        PublicationDeliveryStructure structure = exportsService.netexOrganisations().getValue();

        // JAXB unpacking noise
        List<GeneralOrganisation> organisations = new ArrayList<>();
        structure.getDataObjects().getCompositeFrameOrCommonFrame().forEach(frame -> {
            ResourceFrame.class.cast(frame.getValue()).getOrganisations().getOrganisation_().forEach(dmos -> {
                organisations.add(GeneralOrganisation.class.cast(dmos.getValue()));
            });
        });

        assertThat(organisations.size(), equalTo(2));
        assertGeneralOrganisation(organisations.getFirst(),
            Constants.PUBLIC_VALIDATION_TEST_ID,
            "public-validation-test",
            "FSR:GeneralOrganisation:" + Constants.PUBLIC_VALIDATION_TEST_ID,
            assertContactStructure("https://www.fintraffic.fi"));
        assertGeneralOrganisation(organisations.get(1),
            Constants.FINTRAFFIC_BUSINESS_ID,
            "Fintraffic Oy",
            "FSR:GeneralOrganisation:" + Constants.FINTRAFFIC_BUSINESS_ID,
            assertContactStructure("https://www.fintraffic.fi"));
    }

    private static Consumer<ContactStructure> assertContactStructure(String url) {
        return contactStructure -> {
            assertThat(contactStructure.getUrl(), equalTo(url));
        };
    }

    private void assertGeneralOrganisation(GeneralOrganisation generalOrganisation,
                                           String companyNumber,
                                           String name,
                                           String id,
                                           Consumer<ContactStructure> consumer) {
        assertThat(generalOrganisation.getCompanyNumber(), equalTo(companyNumber));
        assertThat(generalOrganisation.getName().getValue(), equalTo(name));
        assertThat(generalOrganisation.getId(), equalTo(id));
        consumer.accept(generalOrganisation.getContactDetails());
    }
}
