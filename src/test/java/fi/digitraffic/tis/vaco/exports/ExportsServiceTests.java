package fi.digitraffic.tis.vaco.exports;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import org.junit.jupiter.api.Test;
import org.rutebanken.netex.model.GeneralOrganisation;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.ResourceFrame;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

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
        assertGeneralOrganisation(organisations.getFirst(), "public-validation-test-id", "public-validation-test", "FSR:GeneralOrganisation:public-validation-test-id");
        assertGeneralOrganisation(organisations.get(1), "2942108-7", "Fintraffic Oy", "FSR:GeneralOrganisation:2942108-7");
    }

    private void assertGeneralOrganisation(GeneralOrganisation generalOrganisation, String companyNumber, String name, String id) {
        assertThat(generalOrganisation.getCompanyNumber(), equalTo(companyNumber));
        assertThat(generalOrganisation.getName().getValue(), equalTo(name));
        assertThat(generalOrganisation.getId(), equalTo(id));
    }
}
