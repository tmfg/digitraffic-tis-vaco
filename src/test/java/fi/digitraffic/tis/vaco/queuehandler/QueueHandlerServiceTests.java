package fi.digitraffic.tis.vaco.queuehandler;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestConstants;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.Set;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

class QueueHandlerServiceTests extends SpringBootIntegrationTestBase {

    @Autowired
    private QueueHandlerService queueHandlerService;

    @Autowired
    private CompanyRepository companyRepository;

    private ObjectMapper objectMapper;


    private String operatorBusinessId;
    private String operatorName;
    private ObjectNode metadata;
    private ImmutableEntry entryRequest;
    private ImmutableCompany fintrafficCompany;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        operatorBusinessId = "123-4";
        operatorName = "Oppypop Oy";

        metadata = objectMapper.getNodeFactory().objectNode()
            .put("caller", "FINAP")
            .put("operator-name", operatorName);

        entryRequest = ImmutableEntry.builder()
            .publicId(NanoIdUtils.randomNanoId())
            .name("fake gtfs entry")
            .businessId(operatorBusinessId)
            .url(TestConstants.EXAMPLE_URL + "/gtfs.zip")
            .format("gtfs")
            .metadata(metadata)
            .build();

        fintrafficCompany = ImmutableCompany.of(Constants.FINTRAFFIC_BUSINESS_ID, TestConstants.FINTRAFFIC_COMPANY_NAME, true);
    }

    @AfterEach
    void tearDown() {
        companyRepository.deleteByBusinessId(operatorBusinessId);
    }

    @Test
    void autocreatesCompanyOnNewEntryIfSourceIsFinap() {
        assertThat(companyHierarchyService.findByBusinessId(operatorBusinessId).isPresent(), equalTo(false));

        Entry result = queueHandlerService.processQueueEntry(entryRequest).get();

        assertThat(result, Matchers.notNullValue());

        Optional<Company> createdCompany = companyHierarchyService.findByBusinessId(operatorBusinessId);
        assertThat(createdCompany.isPresent(), equalTo(true));

        Company operator = createdCompany.get();
        assertThat(operator.businessId(), equalTo(operatorBusinessId));
        assertThat(operator.name(), equalTo(operatorName));
    }

    @Test
    void wontAutocreateCompanyIfCallerIsNotFinap() {
        entryRequest = entryRequest.withMetadata(metadata.put("caller", "Graham Bell"));

        assertThat(companyHierarchyService.findByBusinessId(operatorBusinessId).isPresent(), equalTo(false));
        // false because entry didn't get created
        assertThat(queueHandlerService.processQueueEntry(entryRequest).isPresent(), equalTo(false));
    }

    @Test
    void wontAutocreateCompanyIfOperatorNameIsMissing() {
        entryRequest = entryRequest.withMetadata(metadata.remove("operator-name"));
        // false because entry didn't get created
        assertThat(queueHandlerService.processQueueEntry(entryRequest).isPresent(), equalTo(false));
    }

    @Test
    void addsContactEmailToCompanysContactEmailsIfPresent() {
        String email = "admin@company.example.fi";
        entryRequest = entryRequest.withMetadata(metadata.put("contact-email", email));

        assertThat(companyHierarchyService.findByBusinessId(operatorBusinessId).isPresent(), equalTo(false));

        queueHandlerService.processQueueEntry(entryRequest).get();

        Optional<Company> createdCompany = companyHierarchyService.findByBusinessId(operatorBusinessId);
        assertThat(createdCompany.isPresent(), equalTo(true));
        Company company = createdCompany.get();

        assertThat(Set.copyOf(company.contactEmails()).contains(email), equalTo(true));
    }

    @Test
    void addsContactEmailToExistingCompanysContactEmailsIfNoEmailsHaveBeenDefined() {
        String email = "admin@company.example.fi";

        // pre-create the company
        Optional<Company> preexistingOperatorCompany = companyHierarchyService.createCompany(ImmutableCompany.of(operatorBusinessId, operatorName, true));
        assertThat(preexistingOperatorCompany.isPresent(), equalTo(true));
        assertThat(companyHierarchyService.findByBusinessId(operatorBusinessId).isPresent(), equalTo(true));

        // add email to metadata
        entryRequest = entryRequest.withMetadata(metadata.put("contact-email", email));
        queueHandlerService.processQueueEntry(entryRequest).get();

        Optional<Company> updatedCompany = companyHierarchyService.findByBusinessId(operatorBusinessId);
        assertThat(updatedCompany.isPresent(), equalTo(true));
        Company company = updatedCompany.get();

        assertThat(Set.copyOf(company.contactEmails()).contains(email), equalTo(true));
    }
}
