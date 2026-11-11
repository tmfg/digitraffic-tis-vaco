package fi.digitraffic.tis.vaco.ruleset;

import tools.jackson.core.type.TypeReference;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.RulesetRecord;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.db.repositories.RulesetRepository;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.RulesetType;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RulesetAccessControllerIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private RulesetRepository rulesetRepository;

    private CompanyRecord ownerCompany;
    private CompanyRecord granteeCompany;
    private RulesetRecord testRuleset;

    @BeforeAll
    static void beforeAll_initializeSqsQueues() {
        createQueue(MessageQueue.ERRORS.getQueueName());
        createQueue(MessageQueue.RULE_RESULTS_INGEST.getQueueName());
        createQueue(MessageQueue.DLQ.getQueueName());
    }

    @BeforeEach
    void setUp() throws Exception {
        // Set up admin auth (following CredentialsControllerSystemTests pattern)
        String oid = "test-admin-oid";
        JwtAuthenticationToken token = TestObjects.jwtAdminAuthenticationToken(oid);
        SecurityContextHolder.getContext().setAuthentication(token);
        injectAuthOverrides(oid, asFintrafficIdGroup(companyHierarchyService.findByBusinessId("2942108-7").get()));

        // Create test companies via API
        ImmutableCompany companyA = TestObjects.aCompany().build();
        ImmutableCompany companyB = TestObjects.aCompany().build();

        apiCall(post("/company").content(toJson(companyA)))
            .andExpect(status().isOk())
            .andReturn();
        apiCall(post("/company").content(toJson(companyB)))
            .andExpect(status().isOk())
            .andReturn();

        ownerCompany = companyRepository.findByBusinessId(companyA.businessId()).get();
        granteeCompany = companyRepository.findByBusinessId(companyB.businessId()).get();

        // Create a test ruleset owned by ownerCompany
        testRuleset = rulesetRepository.createRuleset(
            ownerCompany,
            ImmutableRuleset.of("TEST_GRANT_RULE", "Test grant rule", Category.SPECIFIC, RulesetType.VALIDATION_SYNTAX, TransitDataFormat.GTFS));
    }

    @AfterEach
    void tearDown() {
        rulesetRepository.deleteRuleset(testRuleset);
        companyRepository.deleteByBusinessId(ownerCompany.businessId());
        companyRepository.deleteByBusinessId(granteeCompany.businessId());
    }

    /**
     * Given a valid company and ruleset exist,
     * when an admin POSTs a grant request,
     * then the grant is created successfully (200 OK).
     */
    @Test
    void canCreateGrant() throws Exception {
        String requestBody = """
            {"businessId": "%s"}
            """.formatted(granteeCompany.businessId());

        apiCall(post("/admin/rulesets/" + testRuleset.identifyingName() + "/grants")
            .content(requestBody))
            .andExpect(status().isOk())
            .andReturn();
    }

    /**
     * Given a grant exists,
     * when an admin DELETEs it,
     * then the grant is removed (200 OK).
     */
    @Test
    void canDeleteGrant() throws Exception {
        // given — create grant first
        String requestBody = """
            {"businessId": "%s"}
            """.formatted(granteeCompany.businessId());
        apiCall(post("/admin/rulesets/" + testRuleset.identifyingName() + "/grants")
            .content(requestBody))
            .andExpect(status().isOk())
            .andReturn();

        // when — delete the grant
        apiCall(delete("/admin/rulesets/" + testRuleset.identifyingName() + "/grants/" + granteeCompany.businessId()))
            .andExpect(status().isOk())
            .andReturn();

        // then — the grant is actually gone
        MvcResult response = apiCall(get("/admin/rulesets/" + testRuleset.identifyingName() + "/grants"))
            .andExpect(status().isOk())
            .andReturn();
        List<Company> grants = apiResponse(response, new TypeReference<Resource<List<Company>>>() {}).data();
        assertThat(Streams.collect(grants, Company::businessId), not(hasItem(granteeCompany.businessId())));
    }

    /**
     * Given a non-existent company or ruleset,
     * when an admin attempts to create a grant,
     * then the response is 400 Bad Request.
     */
    @Test
    void creatingGrantForNonExistentCompanyOrRulesetFails() throws Exception {
        // Non-existent company
        String nonExistentCompanyRequest = """
            {"businessId": "%s"}
            """.formatted(UUID.randomUUID().toString());
        apiCall(post("/admin/rulesets/" + testRuleset.identifyingName() + "/grants")
            .content(nonExistentCompanyRequest))
            .andExpect(status().isBadRequest())
            .andReturn();

        // Non-existent ruleset
        String validCompanyRequest = """
            {"businessId": "%s"}
            """.formatted(granteeCompany.businessId());
        apiCall(post("/admin/rulesets/nonexistent.rule/grants")
            .content(validCompanyRequest))
            .andExpect(status().isBadRequest())
            .andReturn();
    }

    /**
     * Given a grant already exists,
     * when the same grant is POSTed again,
     * then the response indicates idempotent behavior (400 — grant already exists).
     */
    @Test
    void duplicateGrantCreationBehavior() throws Exception {
        String requestBody = """
            {"businessId": "%s"}
            """.formatted(granteeCompany.businessId());

        // First grant succeeds
        apiCall(post("/admin/rulesets/" + testRuleset.identifyingName() + "/grants")
            .content(requestBody))
            .andExpect(status().isOk())
            .andReturn();

        // Second identical grant returns 400 (already exists / idempotent no-op surfaced as bad request)
        apiCall(post("/admin/rulesets/" + testRuleset.identifyingName() + "/grants")
            .content(requestBody))
            .andExpect(status().isBadRequest())
            .andReturn();
    }

    /**
     * Given a ruleset has been granted to a company,
     * when an admin lists grants for that ruleset,
     * then the granted company is included in the response.
     */
    @Test
    void canListGrantsForRuleset() throws Exception {
        String requestBody = """
            {"businessId": "%s"}
            """.formatted(granteeCompany.businessId());
        apiCall(post("/admin/rulesets/" + testRuleset.identifyingName() + "/grants")
            .content(requestBody))
            .andExpect(status().isOk())
            .andReturn();

        MvcResult response = apiCall(get("/admin/rulesets/" + testRuleset.identifyingName() + "/grants"))
            .andExpect(status().isOk())
            .andReturn();

        List<Company> grants = apiResponse(response, new TypeReference<Resource<List<Company>>>() {}).data();
        assertThat(Streams.collect(grants, Company::businessId), hasItem(granteeCompany.businessId()));
    }

}
