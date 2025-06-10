package fi.digitraffic.tis.vaco.exports;

import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import fi.digitraffic.tis.vaco.company.service.model.CompanyRole;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rutebanken.netex.model.AvailabilityCondition;
import org.rutebanken.netex.model.ContactStructure;
import org.rutebanken.netex.model.Organisation_VersionStructure;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.ResourceFrame;
import org.rutebanken.netex.model.ValidityConditions_RelStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class ExportsServiceTests extends SpringBootIntegrationTestBase {

    private final static Logger logger = LoggerFactory.getLogger(ExportsServiceTests.class);
    private final static String TEST_COMPANY_BUSINESS_ID = "123456-7";

    @Autowired
    private ExportsService exportsService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyHierarchyService companyHierarchyService;

    @BeforeEach
    void before() {
        var newCompany = companyHierarchyService.createCompany(
            ImmutableCompany.of(TEST_COMPANY_BUSINESS_ID, "Test Authority Operator Oy", true)
        ).orElseThrow();

        var updatedCompany = ImmutableCompany.copyOf(newCompany).withWebsite("https://example.com").withRoles(
            List.of(CompanyRole.AUTHORITY, CompanyRole.OPERATOR)
        );
        companyHierarchyService.editCompany(TEST_COMPANY_BUSINESS_ID, updatedCompany);

    }

    @AfterEach
    void tearDown() {
        companyRepository.deleteByBusinessId(TEST_COMPANY_BUSINESS_ID);
    }

    @Test
    void exportsFullCompanyDetails() {
        PublicationDeliveryStructure structure = exportsService.netexOrganisations().getValue();

        // JAXB unpacking noise
        List<Organisation_VersionStructure> organisations = new ArrayList<>();
        structure.getDataObjects().getCompositeFrameOrCommonFrame().forEach(frame -> {
            ResourceFrame.class.cast(frame.getValue()).getOrganisations().getOrganisation_().forEach(dmos -> {
                organisations.add(Organisation_VersionStructure.class.cast(dmos.getValue()));
            });
        });

        assertThat(organisations.size(), equalTo(4));
        assertOrganisation(getOrganisation(organisations, "FSR:Operator:" + Constants.PUBLIC_VALIDATION_TEST_ID),
            Constants.PUBLIC_VALIDATION_TEST_ID,
            "public-validation-test",
            "FSR:Operator:" + Constants.PUBLIC_VALIDATION_TEST_ID,
            assertValidityConditions("FSR:AvailabilityCondition:1"),
            assertContactStructure("https://www.fintraffic.fi"));
        assertOrganisation(getOrganisation(organisations, "FSR:Operator:" + Constants.FINTRAFFIC_BUSINESS_ID),
            Constants.FINTRAFFIC_BUSINESS_ID,
            "Fintraffic Oy",
            "FSR:Operator:" + Constants.FINTRAFFIC_BUSINESS_ID,
            assertValidityConditions("FSR:AvailabilityCondition:2"),
            assertContactStructure("https://www.fintraffic.fi"));
        assertOrganisation(getOrganisation(organisations, "FSR:Operator:" + TEST_COMPANY_BUSINESS_ID),
            TEST_COMPANY_BUSINESS_ID,
            "Test Authority Operator Oy",
            "FSR:Operator:" + TEST_COMPANY_BUSINESS_ID,
            assertValidityConditions("FSR:AvailabilityCondition:3"),
            assertContactStructure("https://example.com"));
        assertOrganisation(getOrganisation(organisations, "FSR:Authority:" + TEST_COMPANY_BUSINESS_ID),
            TEST_COMPANY_BUSINESS_ID,
            "Test Authority Operator Oy",
            "FSR:Authority:" + TEST_COMPANY_BUSINESS_ID,
            assertValidityConditions("FSR:AvailabilityCondition:4"),
            assertContactStructure("https://example.com"));
    }

    private static Consumer<ValidityConditions_RelStructure> assertValidityConditions(String expectedAvailabilityConditionId) {
        return validityConditionsRelStructure -> {
            switch (validityConditionsRelStructure.getValidityConditionRefOrValidBetweenOrValidityCondition_()) {
                case AvailabilityCondition availabilityCondition -> {
                    assertThat(availabilityCondition.getId(), equalTo(expectedAvailabilityConditionId));
                }
                case Object o -> {
                    logger.warn("Unverified type {}", o.getClass());
                }
            }
        };
    }

    private static Consumer<ContactStructure> assertContactStructure(String url) {
        return contactStructure -> {
            assertThat(contactStructure.getUrl(), equalTo(url));
        };
    }

    private void assertOrganisation(Organisation_VersionStructure organisation,
                                    String companyNumber,
                                    String name,
                                    String id,
                                    Consumer<ValidityConditions_RelStructure> validityConditionsAsserter,
                                    Consumer<ContactStructure> contactStructureAsserter) {
        assertThat(organisation.getCompanyNumber(), equalTo(companyNumber));
        assertThat(organisation.getName().getValue(), equalTo(name));
        assertThat(organisation.getId(), equalTo(id));
        assertThat("Version attribute for organisations must be set to hardcoded '1'", organisation.getVersion(), equalTo("1"));
        validityConditionsAsserter.accept(organisation.getValidityConditions());
        contactStructureAsserter.accept(organisation.getContactDetails());
    }

    private Organisation_VersionStructure getOrganisation(List<Organisation_VersionStructure> organisations, String id) {
        return organisations.stream().filter(o -> o.getId().equals(id)).findFirst().orElseThrow();
    }
}
