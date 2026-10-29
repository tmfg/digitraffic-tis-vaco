package fi.digitraffic.tis.vaco.exports;

import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import fi.digitraffic.tis.vaco.company.service.model.CompanyRole;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.fintrafficid.model.FintrafficIdGroup;
import fi.digitraffic.tis.vaco.fintrafficid.model.ImmutableFintrafficIdGroup;
import fi.digitraffic.tis.vaco.fintrafficid.model.ImmutableOrganizationData;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rutebanken.netex.model.Authority_VersionStructure;
import org.rutebanken.netex.model.AvailabilityCondition;
import org.rutebanken.netex.model.ContactStructure;
import org.rutebanken.netex.model.Operator_VersionStructure;
import org.rutebanken.netex.model.Organisation_VersionStructure;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.ResourceFrame;
import org.rutebanken.netex.model.ValidityConditions_RelStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class ExportsServiceTests extends SpringBootIntegrationTestBase {

    private static final Logger logger = LoggerFactory.getLogger(ExportsServiceTests.class);
    private static final String TEST_COMPANY_BUSINESS_ID = "123456-7";

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

        assertThat(organisations.size(), equalTo(5));
        assertOperatorOrganisation(getOperator(organisations, Constants.PUBLIC_VALIDATION_TEST_ID),
            Constants.PUBLIC_VALIDATION_TEST_ID,
            "public-validation-test",
            "FSR:Operator:" + Constants.PUBLIC_VALIDATION_TEST_ID,
            assertValidityConditions("FSR:AvailabilityCondition:1"),
            assertContactStructure("https://www.fintraffic.fi"));
        assertOperatorOrganisation(getOperator(organisations, Constants.FINTRAFFIC_BUSINESS_ID),
            Constants.FINTRAFFIC_BUSINESS_ID,
            "Fintraffic Oy",
            "FSR:Operator:" + Constants.FINTRAFFIC_BUSINESS_ID,
            assertValidityConditions("FSR:AvailabilityCondition:2"),
            assertContactStructure("https://www.fintraffic.fi"));
        assertOperatorOrganisation(getOperator(organisations, TEST_COMPANY_BUSINESS_ID),
            TEST_COMPANY_BUSINESS_ID,
            "Test Authority Operator Oy",
            "FSR:Operator:" + TEST_COMPANY_BUSINESS_ID,
            assertValidityConditions("FSR:AvailabilityCondition:3"),
            assertContactStructure("https://example.com"));
        assertAuthorityOrganisation(getAuthority(organisations, TEST_COMPANY_BUSINESS_ID),
            TEST_COMPANY_BUSINESS_ID,
            "Test Authority Operator Oy",
            "FSR:Authority:" + TEST_COMPANY_BUSINESS_ID,
            assertValidityConditions("FSR:AvailabilityCondition:4"),
            assertContactStructure("https://example.com"));
        assertOperatorOrganisation(getOperator(organisations, Constants.TRAFICOM_BUSINESS_ID),
            Constants.TRAFICOM_BUSINESS_ID,
            "Liikenne- ja viestintävirasto Traficom",
            "FSR:Operator:" + Constants.TRAFICOM_BUSINESS_ID,
            assertValidityConditions("FSR:AvailabilityCondition:5"),
            assertContactStructure("https://traficom.fi/fi"));
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
                                    Consumer<ValidityConditions_RelStructure> validityConditionsAsserter) {
        assertThat(organisation.getCompanyNumber(), equalTo(companyNumber));
        assertThat(organisation.getName().getValue(), equalTo(name));
        assertThat(organisation.getId(), equalTo(id));
        assertThat("Version attribute for organisations must be set to hardcoded '1'", organisation.getVersion(), equalTo("1"));
        validityConditionsAsserter.accept(organisation.getValidityConditions());
    }

    private void assertAuthorityOrganisation(Authority_VersionStructure organisation,
                                             String companyNumber,
                                             String name,
                                             String id,
                                             Consumer<ValidityConditions_RelStructure> validityConditionsAsserter,
                                             Consumer<ContactStructure> contactStructureAsserter) {
        assertOrganisation(organisation, companyNumber, name, id, validityConditionsAsserter);
        contactStructureAsserter.accept(organisation.getContactDetails());
    }

    private void assertOperatorOrganisation(Operator_VersionStructure organisation,
                                            String companyNumber,
                                            String name,
                                            String id,
                                            Consumer<ValidityConditions_RelStructure> validityConditionsAsserter,
                                            Consumer<ContactStructure> contactStructureAsserter) {
        assertOrganisation(organisation, companyNumber, name, id, validityConditionsAsserter);
        contactStructureAsserter.accept(organisation.getCustomerServiceContactDetails());
    }

    @Test
    void netexAuthoritiesUsesCodespaceBasedIds() {
        String testBusinessId = "987654-3";
        var company = companyHierarchyService.createCompany(
            ImmutableCompany.of(testBusinessId, "Test Authority TST", true)
        ).orElseThrow();
        companyHierarchyService.editCompany(testBusinessId,
            ImmutableCompany.copyOf(company)
                .withRoles(List.of(CompanyRole.AUTHORITY))
                .withCodespaces(List.of("TST"))
                .withWebsite("https://tst.example.com"));

        try {
            PublicationDeliveryStructure structure = exportsService.netexAuthorities().getValue();
            List<Organisation_VersionStructure> organisations = new ArrayList<>();
            structure.getDataObjects().getCompositeFrameOrCommonFrame().forEach(frame ->
                ResourceFrame.class.cast(frame.getValue()).getOrganisations().getOrganisation_().forEach(dmos ->
                    organisations.add(Organisation_VersionStructure.class.cast(dmos.getValue()))));

            // Company from @BeforeEach has AUTHORITY role but no codespace — must be absent
            assertThat(organisations.stream().noneMatch(o -> TEST_COMPANY_BUSINESS_ID.equals(o.getCompanyNumber())), equalTo(true));

            // Our company must appear with codespace-based ID
            Authority_VersionStructure authority =
                (Authority_VersionStructure) getOrganisation(organisations, "TST:Authority:TST");
            assertThat(authority.getCompanyNumber(), equalTo(testBusinessId));
            assertThat(authority.getName().getValue(), equalTo("Test Authority TST"));
            assertThat(authority.getVersion(), equalTo("1"));
            assertThat(authority.getContactDetails().getUrl(), equalTo("https://tst.example.com"));
            assertThat(authority.getValidityConditions(), equalTo(null));
        } finally {
            companyRepository.deleteByBusinessId(testBusinessId);
        }
    }

    @Test
    void netexAuthoritiesOmitsContactDetailsWhenNoWebsite() {
        String testBusinessId = "111222-3";
        var company = companyHierarchyService.createCompany(
            ImmutableCompany.of(testBusinessId, "No Website Authority", true)
        ).orElseThrow();
        companyHierarchyService.editCompany(testBusinessId,
            ImmutableCompany.copyOf(company)
                .withRoles(List.of(CompanyRole.AUTHORITY))
                .withCodespaces(List.of("NWA")));

        try {
            PublicationDeliveryStructure structure = exportsService.netexAuthorities().getValue();
            List<Organisation_VersionStructure> organisations = extractOrganisations(structure);

            Authority_VersionStructure authority =
                (Authority_VersionStructure) getOrganisation(organisations, "NWA:Authority:NWA");
            assertThat(authority.getCompanyNumber(), equalTo(testBusinessId));
            assertThat(authority.getContactDetails(), equalTo(null));
        } finally {
            companyRepository.deleteByBusinessId(testBusinessId);
        }
    }

    @Test
    void netexAuthoritiesExcludesOperatorOnlyCompanies() {
        String testBusinessId = "333444-5";
        var company = companyHierarchyService.createCompany(
            ImmutableCompany.of(testBusinessId, "Operator Only Oy", true)
        ).orElseThrow();
        companyHierarchyService.editCompany(testBusinessId,
            ImmutableCompany.copyOf(company)
                .withRoles(List.of(CompanyRole.OPERATOR))
                .withCodespaces(List.of("OPR"))
                .withWebsite("https://operator.example.com"));

        try {
            PublicationDeliveryStructure structure = exportsService.netexAuthorities().getValue();
            List<Organisation_VersionStructure> organisations = extractOrganisations(structure);

            assertThat(organisations.stream().noneMatch(o -> testBusinessId.equals(o.getCompanyNumber())), equalTo(true));
        } finally {
            companyRepository.deleteByBusinessId(testBusinessId);
        }
    }

    @Test
    void publicNetexOrganisationsHasSameOrganisationsAndIdsAsNetexOrganisations() {
        String noWebsiteBusinessId = "555666-7";
        companyHierarchyService.createCompany(ImmutableCompany.of(noWebsiteBusinessId, "No Website Oy", true)).orElseThrow();
        companyHierarchyService.editCompany(noWebsiteBusinessId,
            ImmutableCompany.of(noWebsiteBusinessId, "No Website Oy", true)
                .withRoles(List.of(CompanyRole.AUTHORITY, CompanyRole.OPERATOR)));

        try {
            List<Organisation_VersionStructure> allOrganisations = extractOrganisations(exportsService.netexOrganisations().getValue());
            List<Organisation_VersionStructure> publicOrganisations = extractOrganisations(exportsService.publicNetexOrganisations().getValue());

            assertThat(Streams.collect(publicOrganisations, Organisation_VersionStructure::getId),
                equalTo(Streams.collect(allOrganisations, Organisation_VersionStructure::getId)));
            assertThat(Streams.collect(publicOrganisations, o -> o.getName().getValue()),
                equalTo(Streams.collect(allOrganisations, o -> o.getName().getValue())));
            // company with both roles is present as both operator and authority
            assertThat(getOperator(publicOrganisations, TEST_COMPANY_BUSINESS_ID).getCompanyNumber(), equalTo(TEST_COMPANY_BUSINESS_ID));
            assertThat(getAuthority(publicOrganisations, TEST_COMPANY_BUSINESS_ID).getCompanyNumber(), equalTo(TEST_COMPANY_BUSINESS_ID));
            // company without a website is excluded
            assertThat(Streams.collect(publicOrganisations, Organisation_VersionStructure::getCompanyNumber),
                not(hasItem(noWebsiteBusinessId)));
        } finally {
            companyRepository.deleteByBusinessId(noWebsiteBusinessId);
        }
    }

    @Test
    void publicNetexOrganisationsCarriesOnlyUrlAsContactDetails() {
        List<Organisation_VersionStructure> organisations = extractOrganisations(exportsService.publicNetexOrganisations().getValue());

        Authority_VersionStructure authority = getAuthority(organisations, TEST_COMPANY_BUSINESS_ID);
        assertThat(authority.getContactDetails().getUrl(), equalTo("https://example.com"));
        assertUrlOnly(authority.getContactDetails());
        assertThat(authority.getValidityConditions(), notNullValue());

        Operator_VersionStructure operator = getOperator(organisations, TEST_COMPANY_BUSINESS_ID);
        assertThat(operator.getCustomerServiceContactDetails().getUrl(), equalTo("https://example.com"));
        assertUrlOnly(operator.getCustomerServiceContactDetails());
        assertThat(operator.getContactDetails(), nullValue());
    }

    @Test
    void publicNetexOrganisationsDoesNotExposePersonalData() throws JAXBException {
        String businessId = "777888-9";
        String email = "secret.person@example.org";
        String contactName = "Secret Contact Person";
        String phone = "+358401234567";
        FintrafficIdGroup group = ImmutableFintrafficIdGroup.builder()
            .id("ad-group-" + businessId)
            .displayName("Personal Data Oy")
            .organizationData(ImmutableOrganizationData.builder()
                .businessId(businessId)
                .phoneNumber(phone)
                .contactName(contactName)
                .build())
            .build();
        injectAuthOverrides("oid-" + businessId, group);
        companyHierarchyService.createCompany(ImmutableCompany.of(businessId, "Personal Data Oy", true)).orElseThrow();
        companyHierarchyService.editCompany(businessId,
            ImmutableCompany.of(businessId, "Personal Data Oy", true)
                .withAdGroupId(group.id())
                .withContactEmails(List.of(email))
                .withWebsite("https://personal.example.com")
                .withRoles(List.of(CompanyRole.AUTHORITY, CompanyRole.OPERATOR)));

        try {
            // the existing export does include the personal data, so the checks below are meaningful
            Authority_VersionStructure fullAuthority = getAuthority(
                extractOrganisations(exportsService.netexOrganisations().getValue()), businessId);
            assertThat(fullAuthority.getContactDetails().getPhone(), equalTo(phone));
            assertThat(fullAuthority.getContactDetails().getContactPerson().getValue(), equalTo(contactName));

            JAXBElement<PublicationDeliveryStructure> document = exportsService.publicNetexOrganisations();
            List<Organisation_VersionStructure> organisations = extractOrganisations(document.getValue());
            assertThat(Streams.collect(organisations, Organisation_VersionStructure::getCompanyNumber), hasItem(businessId));
            assertUrlOnly(getAuthority(organisations, businessId).getContactDetails());
            assertUrlOnly(getOperator(organisations, businessId).getCustomerServiceContactDetails());

            StringWriter xml = new StringWriter();
            JAXBContext.newInstance(PublicationDeliveryStructure.class).createMarshaller().marshal(document, xml);
            String output = xml.toString();
            assertThat(output, not(containsString("ContactPerson")));
            assertThat(output, not(containsString("Phone")));
            assertThat(output, not(containsString(email)));
            assertThat(output, not(containsString(contactName)));
            assertThat(output, not(containsString(phone)));
        } finally {
            companyRepository.deleteByBusinessId(businessId);
        }
    }

    private static void assertUrlOnly(ContactStructure contactStructure) {
        assertThat(contactStructure.getContactPerson(), nullValue());
        assertThat(contactStructure.getPhone(), nullValue());
        assertThat(contactStructure.getEmail(), nullValue());
        assertThat(contactStructure.getFax(), nullValue());
        assertThat(contactStructure.getFurtherDetails(), nullValue());
    }

    private List<Organisation_VersionStructure> extractOrganisations(PublicationDeliveryStructure structure) {
        List<Organisation_VersionStructure> organisations = new ArrayList<>();
        structure.getDataObjects().getCompositeFrameOrCommonFrame().forEach(frame ->
            ResourceFrame.class.cast(frame.getValue()).getOrganisations().getOrganisation_().forEach(dmos ->
                organisations.add(Organisation_VersionStructure.class.cast(dmos.getValue()))));
        return organisations;
    }

    private Organisation_VersionStructure getOrganisation(List<Organisation_VersionStructure> organisations, String id) {
        return organisations.stream().filter(o -> o.getId().equals(id)).findFirst().orElseThrow();
    }

    private Operator_VersionStructure getOperator(List<Organisation_VersionStructure> organisations, String id) {
        return (Operator_VersionStructure) getOrganisation(organisations, "FSR:Operator:" + id);
    }

    private Authority_VersionStructure getAuthority(List<Organisation_VersionStructure> organisations, String id) {
        return (Authority_VersionStructure) getOrganisation(organisations, "FSR:Authority:" + id);
    }
}
