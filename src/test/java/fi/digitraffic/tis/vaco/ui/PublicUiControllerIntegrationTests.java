package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.api.model.queue.CreateEntryRequest;
import fi.digitraffic.tis.vaco.api.model.queue.ImmutableCreateEntryRequest;
import fi.digitraffic.tis.vaco.api.model.credentials.ImmutableCreateCredentialsRequest;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.credentials.CredentialsService;
import fi.digitraffic.tis.vaco.credentials.model.CredentialsType;
import fi.digitraffic.tis.vaco.credentials.model.ImmutableHttpBasicAuthenticationDetails;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
import fi.digitraffic.tis.vaco.db.repositories.FindingRepository;
import fi.digitraffic.tis.vaco.db.repositories.RulesetRepository;
import fi.digitraffic.tis.vaco.db.repositories.TaskRepository;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.findings.model.FindingSeverity;
import fi.digitraffic.tis.vaco.fintrafficid.model.FintrafficIdGroup;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The data delivery, company entry, results, package and test data endpoints are open to everyone. These tests run
 * with no authentication unless they sign in as the admin.
 *
 * <p>IssuerRoutingAuthenticationTests covers the URL rules, because this context has the filters switched off.
 */
class PublicUiControllerIntegrationTests extends SpringBootIntegrationTestBase {

    private static final String PUBLIC_ID = "publicId";
    private static final String DATA = "data";
    private static final String SECRET_QUERY = "token=secret";
    private static final String ADMIN_OID = "Admin Adminson";
    private static final String RESULT = "result";
    private static final String ADMIN_EXPORT_PATH = "/ui/admin/data-delivery/export";
    private static final String PUBLIC_EXPORT_PATH = "/ui/data-delivery/export";
    private static final String TEST_RESULTS_PATH = "/ui/data-delivery/test-results";
    private static final String COMPANY_ENTRIES_PATH = "/ui/pages/company-entries/";
    private static final String COMPANY_NAME = "companyName";
    private static final String UNKNOWN_BUSINESS_ID = "0000000-0";

    private final ObjectMapper plainMapper = new ObjectMapper();

    private static String entryStatePath(String publicId) {
        return "/ui/entries/" + publicId + "/state";
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CredentialsService credentialsService;
    @Autowired
    private EntryService entryService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private PackagesService packagesService;
    @Autowired
    private EntryRepository entryRepository;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private RulesetRepository rulesetRepository;
    @Autowired
    private FindingRepository findingRepository;
    @Autowired
    private RecordMapper recordMapper;

    @BeforeAll
    static void beforeAll(@Autowired VacoProperties vacoProperties) {
        createBucket(vacoProperties.s3ProcessingBucket());
        createBucket(vacoProperties.s3PackagesBucket());
    }

    private void signInAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(TestObjects.jwtAdminAuthenticationToken(ADMIN_OID));
        injectAuthOverrides(ADMIN_OID, asFintrafficIdGroup(companyHierarchyService.findByBusinessId(Constants.FINTRAFFIC_BUSINESS_ID).get()));
    }

    private void signOut() {
        SecurityContextHolder.clearContext();
    }

    private String createCredentials() {
        return credentialsService.createCredentials(ImmutableCreateCredentialsRequest.of(
                CredentialsType.HTTP_BASIC,
                "public test credentials " + UUID.randomUUID(),
                "testing credentials",
                Constants.FINTRAFFIC_BUSINESS_ID,
                ImmutableHttpBasicAuthenticationDetails.of("userId", "password")))
            .orElseThrow()
            .publicId();
    }

    private static String uniqueUrl() {
        return "https://example.fi/" + UUID.randomUUID() + ".zip?" + SECRET_QUERY;
    }

    private ImmutableCreateEntryRequest.Builder richRequest(String url, String credentialsId) {
        ObjectNode validation = plainMapper.createObjectNode().put("name", RuleName.GTFS_CANONICAL);
        validation.putObject("config");
        ObjectNode conversion = plainMapper.createObjectNode().put("name", RuleName.GTFS2NETEX_FINTRAFFIC);
        conversion.putObject("config");
        return TestObjects.aValidationEntryRequest()
            .url(url)
            .name("public data test")
            .credentials(credentialsId)
            .validations(List.of(validation))
            .conversions(List.of(conversion));
    }

    /**
     * Creates an entry as the admin and marks it successful, so that the data delivery overview lists it.
     */
    private String createVisibleEntry(String url, String credentialsId) throws Exception {
        signInAsAdmin();
        MvcResult response = apiCall(post("/queue").content(toJson(richRequest(url, credentialsId).build())))
            .andExpect(status().isOk())
            .andReturn();
        String publicId = apiResponse(response).get(DATA).get(PUBLIC_ID).stringValue();
        jdbc.update("UPDATE entry SET status = 'success' WHERE public_id = ?", publicId);
        signOut();
        return publicId;
    }

    private void addPackage(String entryPublicId, String taskName) {
        Entry entry = entryService.findEntry(entryPublicId).orElseThrow();
        Task task = taskService.findTask(entryPublicId, taskName).orElseThrow();
        packagesService.createPackage(
            entry,
            task,
            RESULT,
            ImmutableS3Path.of(entryPublicId + "/" + task.publicId()),
            "result.zip",
            p -> true);
        // the entry is cached without the new package
        entryService.reload(entry);
    }

    private JsonNode dataDeliveryItem(JsonNode body, String entryPublicId) {
        for (JsonNode item : body) {
            if (entryPublicId.equals(item.get(DATA).get(PUBLIC_ID).stringValue())) {
                return item.get(DATA);
            }
        }
        throw new AssertionError("Entry " + entryPublicId + " not in data delivery overview");
    }

    private String exportCsv() throws Exception {
        MvcResult result = mockMvc.perform(get(ADMIN_EXPORT_PATH).param("language", "fi")).andReturn();
        result.getAsyncResult(10_000);
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private Company createCompany(String businessId) {
        return companyHierarchyService.createCompany(TestObjects.aCompany().name("Company " + businessId).businessId(businessId).build())
            .or(() -> companyHierarchyService.findByBusinessId(businessId))
            .orElseThrow();
    }

    private static String uniqueBusinessId() {
        return "pub-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String createSuccessfulEntryFor(String businessId) {
        return entryRepository.create(
                TestObjects.anEntry().url(uniqueUrl()).businessId(businessId).build(), Optional.empty(), Optional.empty())
            .map(e -> {
                entryRepository.markStatus(e, Status.SUCCESS);
                return e.publicId();
            })
            .orElseThrow();
    }

    private void signInAsUserOf(String oid, Company... companies) {
        SecurityContextHolder.getContext().setAuthentication(TestObjects.jwtAuthenticationToken(oid));
        injectAuthOverrides(oid, Arrays.stream(companies).map(this::asFintrafficIdGroup).toArray(FintrafficIdGroup[]::new));
    }

    private void signInAsAdminOf(String oid, Company... companies) {
        SecurityContextHolder.getContext().setAuthentication(TestObjects.jwtAdminAuthenticationToken(oid));
        injectAuthOverrides(oid, Arrays.stream(companies).map(this::asFintrafficIdGroup).toArray(FintrafficIdGroup[]::new));
    }

    private JsonNode testResults() throws Exception {
        MvcResult result = apiCall(get(TEST_RESULTS_PATH)).andExpect(status().isOk()).andReturn();
        return apiResponse(result);
    }

    private static List<String> publicIds(JsonNode body) {
        List<String> ids = new java.util.ArrayList<>();
        body.forEach(item -> ids.add(item.get(DATA).get(PUBLIC_ID).stringValue()));
        return ids;
    }

    private static List<String> businessIds(JsonNode body) {
        return java.util.stream.StreamSupport.stream(body.spliterator(), false)
            .map(item -> item.get(DATA).get("businessId").stringValue())
            .distinct()
            .toList();
    }

    private Entry submitTestData(String businessId) throws Exception {
        CreateEntryRequest request = richRequest("https://example.fi/" + UUID.randomUUID() + ".zip", createCredentials())
            .businessId(businessId)
            .context("some-context")
            .sendNotifications(true)
            .notifications(List.of("someone@example.fi"))
            .build();
        MvcResult result = apiCall(post("/ui/queue").content(toJson(request)))
            .andExpect(status().isOk())
            .andReturn();
        return entryService.findEntry(apiResponse(result).get(DATA).get(PUBLIC_ID).stringValue()).orElseThrow();
    }

    private JsonNode entryState(String publicId) throws Exception {
        MvcResult result = apiCall(get(entryStatePath(publicId)))
            .andExpect(status().isOk())
            .andReturn();
        return apiResponse(result).get(DATA);
    }

    private static List<String> packageNames(JsonNode state, String taskName) {
        for (JsonNode report : state.get("reports")) {
            if (taskName.equals(report.get("name").stringValue())) {
                List<String> names = new java.util.ArrayList<>();
                report.get("packages").forEach(p -> names.add(p.get(DATA).get("name").stringValue()));
                return names;
            }
        }
        return List.of();
    }

    private static String packagePath(String entryPublicId, String taskName) {
        return "/ui/packages/" + entryPublicId + "/" + taskName + "/" + RESULT;
    }

    // T1
    @Test
    void anonymousDataDeliveryHasUrlWithoutQueryString() throws Exception {
        String url = uniqueUrl();
        String id = createVisibleEntry(url, null);

        MvcResult result = apiCall(get("/ui/data-delivery")).andExpect(status().isOk()).andReturn();
        JsonNode body = apiResponse(result);

        assertAll(
            () -> assertThat(dataDeliveryItem(body, id).get("url").stringValue(), equalTo(url.substring(0, url.indexOf('?')))),
            () -> assertThat(result.getResponse().getContentAsString(), not(containsString(SECRET_QUERY))));
    }

    // T1b
    @Test
    void adminDataDeliveryHasFullUrl() throws Exception {
        String url = uniqueUrl();
        String id = createVisibleEntry(url, null);

        signInAsAdmin();
        MvcResult result = apiCall(get("/ui/data-delivery")).andExpect(status().isOk()).andReturn();

        assertThat(dataDeliveryItem(apiResponse(result), id).get("url").stringValue(), equalTo(url));
    }

    // T2
    @Test
    void visitorGetsNoCsvFromPublicExportPath() throws Exception {
        createVisibleEntry(uniqueUrl(), null);

        MvcResult result = mockMvc.perform(get(PUBLIC_EXPORT_PATH).param("language", "fi")).andReturn();

        assertThat(result.getResponse().getStatus(), equalTo(404));
    }

    // G3
    @Test
    void adminExportHasFullUrl() throws Exception {
        String url = uniqueUrl();
        createVisibleEntry(url, null);

        signInAsAdmin();

        assertThat(exportCsv(), containsString(url));
    }

    // T3
    @Test
    void anonymousCanListCompanyEntries() throws Exception {
        String id = createVisibleEntry(uniqueUrl(), null);

        MvcResult result = apiCall(get(COMPANY_ENTRIES_PATH + Constants.FINTRAFFIC_BUSINESS_ID))
            .andExpect(status().isOk())
            .andReturn();

        List<String> ids = new java.util.ArrayList<>();
        apiResponse(result).get(DATA).get("entries").forEach(e -> ids.add(e.get(PUBLIC_ID).stringValue()));
        assertThat(ids.contains(id), equalTo(true));
        String expectedName = companyHierarchyService.findByBusinessId(Constants.FINTRAFFIC_BUSINESS_ID).orElseThrow().name();
        assertThat(apiResponse(result).get(DATA).get(COMPANY_NAME).stringValue(), equalTo(expectedName));
    }

    // T22
    @Test
    void anonymousGetsNotFoundForUnknownCompanyEntries() throws Exception {
        assertThat(companyHierarchyService.findByBusinessId(UNKNOWN_BUSINESS_ID).isPresent(), equalTo(false));

        apiCall(get(COMPANY_ENTRIES_PATH + UNKNOWN_BUSINESS_ID)).andExpect(status().isNotFound());
    }

    // T23
    @Test
    void adminAliasKeepsEmptyListForUnknownCompany() throws Exception {
        assertThat(companyHierarchyService.findByBusinessId(UNKNOWN_BUSINESS_ID).isPresent(), equalTo(false));
        signInAsAdmin();

        MvcResult result = apiCall(get("/ui/pages/admin-company-entries/" + UNKNOWN_BUSINESS_ID))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(apiResponse(result).get(DATA).get("entries").size(), equalTo(0));
    }

    // T24
    @Test
    void anonymousGetsNoNameForCompanyWithNullName() throws Exception {
        String businessId = uniqueBusinessId();
        assertThat(companyHierarchyService.findByBusinessId(businessId).isPresent(), equalTo(false));
        companyHierarchyService.createCompany(TestObjects.aCompany().name(null).businessId(businessId).build()).orElseThrow();
        try {
            MvcResult result = apiCall(get(COMPANY_ENTRIES_PATH + businessId))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode name = apiResponse(result).get(DATA).get(COMPANY_NAME);
            assertThat(name == null || name.isNull(), equalTo(true));
        } finally {
            // the data delivery overview cannot map a company with no name, so other tests must not see this one
            companyHierarchyService.deleteCompany(businessId);
        }
    }

    // G5
    @Test
    void adminPagesAtOldPathsStillWork() throws Exception {
        signInAsAdmin();

        apiCall(get("/ui/admin/data-delivery")).andExpect(status().isOk());
        apiCall(get("/ui/pages/admin-company-entries/" + Constants.FINTRAFFIC_BUSINESS_ID)).andExpect(status().isOk());
        apiCall(get("/ui/entries").param("full", "true")).andExpect(status().isOk());
    }

    // T4
    @Test
    void anonymousStateHasMaskedUrl() throws Exception {
        String url = uniqueUrl();
        String id = createVisibleEntry(url, null);

        MvcResult result = apiCall(get(entryStatePath(id))).andExpect(status().isOk()).andReturn();

        assertAll(
            () -> assertThat(apiResponse(result).get(DATA).get("entry").get(DATA).get("url").stringValue(), equalTo(url.substring(0, url.indexOf('?')))),
            () -> assertThat(result.getResponse().getContentAsString(), not(containsString(SECRET_QUERY))));
    }

    // G4
    @Test
    void adminStateHasFullUrl() throws Exception {
        String url = uniqueUrl();
        String id = createVisibleEntry(url, null);

        signInAsAdmin();

        assertThat(entryState(id).get("entry").get(DATA).get("url").stringValue(), equalTo(url));
    }

    // T5
    @Test
    void anonymousStateIgnoresGarbageMagicParameter() throws Exception {
        String id = createVisibleEntry(uniqueUrl(), null);

        apiCall(get(entryStatePath(id)).param("magic", "garbage")).andExpect(status().isOk());
    }

    // T6
    @Test
    void anonymousCannotDownloadInputPackageOfEntryWithCredentials() throws Exception {
        String id = createVisibleEntry(uniqueUrl(), createCredentials());
        addPackage(id, DownloadRule.PREPARE_DOWNLOAD_TASK);

        apiCall(get(packagePath(id, DownloadRule.PREPARE_DOWNLOAD_TASK))).andExpect(status().isNotFound());
        assertThat(packageNames(entryState(id), DownloadRule.PREPARE_DOWNLOAD_TASK), empty());
    }

    // T7
    @Test
    void anonymousCanDownloadInputPackageOfEntryWithoutCredentials() throws Exception {
        String id = createVisibleEntry(uniqueUrl(), null);
        addPackage(id, DownloadRule.PREPARE_DOWNLOAD_TASK);

        apiCall(get(packagePath(id, DownloadRule.PREPARE_DOWNLOAD_TASK))).andExpect(status().isOk());
        assertThat(packageNames(entryState(id), DownloadRule.PREPARE_DOWNLOAD_TASK), contains(RESULT));
    }

    // T8
    @Test
    void adminCanDownloadInputPackageOfEntryWithCredentials() throws Exception {
        String id = createVisibleEntry(uniqueUrl(), createCredentials());
        addPackage(id, DownloadRule.PREPARE_DOWNLOAD_TASK);

        signInAsAdmin();

        apiCall(get(packagePath(id, DownloadRule.PREPARE_DOWNLOAD_TASK))).andExpect(status().isOk());
    }

    // T15
    @Test
    void conversionPackagesOfEntryWithCredentialsAreHiddenFromEveryoneButAdmin() throws Exception {
        String id = createVisibleEntry(uniqueUrl(), createCredentials());
        addPackage(id, RuleName.GTFS2NETEX_FINTRAFFIC);

        apiCall(get(packagePath(id, RuleName.GTFS2NETEX_FINTRAFFIC))).andExpect(status().isNotFound());
        assertThat(packageNames(entryState(id), RuleName.GTFS2NETEX_FINTRAFFIC), empty());

        signInAsAdmin();
        apiCall(get(packagePath(id, RuleName.GTFS2NETEX_FINTRAFFIC))).andExpect(status().isOk());
        assertThat(packageNames(entryState(id), RuleName.GTFS2NETEX_FINTRAFFIC), contains(RESULT));
    }

    // T9
    @Test
    void anonymousTestDataEntryGoesToPublicTestCompany() throws Exception {
        Company before = companyHierarchyService.findByBusinessId(Constants.PUBLIC_VALIDATION_TEST_ID).orElseThrow();
        long companiesBefore = jdbc.queryForObject("SELECT count(*) FROM company", Long.class);
        ObjectNode metadata = plainMapper.createObjectNode()
            .put("caller", "FINAP")
            .put("operator-name", "Anonymous Operator Oy");
        CreateEntryRequest request = richRequest("https://example.fi/anonymous.zip", createCredentials())
            .businessId(Constants.FINTRAFFIC_BUSINESS_ID)
            .context("someone-elses-context")
            .metadata(metadata)
            .build();

        MvcResult result = apiCall(post("/ui/queue").content(toJson(request)))
            .andExpect(status().isOk())
            .andReturn();
        Entry stored = entryService.findEntry(apiResponse(result).get(DATA).get(PUBLIC_ID).stringValue()).orElseThrow();

        assertAll(
            () -> assertThat(before.businessId(), equalTo(Constants.PUBLIC_VALIDATION_TEST_ID)),
            () -> assertThat(stored.businessId(), equalTo(Constants.PUBLIC_VALIDATION_TEST_ID)),
            () -> assertThat(stored.context(), nullValue()),
            () -> assertThat(stored.credentials(), nullValue()),
            () -> assertThat(stored.metadata(), nullValue()),
            () -> assertThat(jdbc.queryForObject("SELECT count(*) FROM company", Long.class), equalTo(companiesBefore)));
    }

    // T17
    @Test
    void visitorTestResultsHoldOnlyPublicTestCompanyWithMaskedUrls() throws Exception {
        Company other = createCompany(uniqueBusinessId());
        String publicEntry = createSuccessfulEntryFor(Constants.PUBLIC_VALIDATION_TEST_ID);
        String otherEntry = createSuccessfulEntryFor(other.businessId());

        MvcResult result = apiCall(get(TEST_RESULTS_PATH)).andExpect(status().isOk()).andReturn();
        JsonNode body = apiResponse(result);

        assertAll(
            () -> assertThat(publicIds(body).contains(publicEntry), equalTo(true)),
            () -> assertThat(publicIds(body).contains(otherEntry), equalTo(false)),
            () -> assertThat(businessIds(body), contains(Constants.PUBLIC_VALIDATION_TEST_ID)),
            () -> assertThat(result.getResponse().getContentAsString(), not(containsString(SECRET_QUERY))));
    }

    // T18
    @Test
    void userTestResultsHoldRowsOfAllOwnCompaniesInOneList() throws Exception {
        Company x = createCompany(uniqueBusinessId());
        Company y = createCompany(uniqueBusinessId());
        Company z = createCompany(uniqueBusinessId());
        String entryX = createSuccessfulEntryFor(x.businessId());
        String entryY = createSuccessfulEntryFor(y.businessId());
        String entryZ = createSuccessfulEntryFor(z.businessId());
        String entryPublic = createSuccessfulEntryFor(Constants.PUBLIC_VALIDATION_TEST_ID);
        signInAsUserOf("results-user-" + UUID.randomUUID(), x, y);

        List<String> ids = publicIds(testResults());

        assertAll(
            () -> assertThat(ids.contains(entryX), equalTo(true)),
            () -> assertThat(ids.contains(entryY), equalTo(true)),
            () -> assertThat(ids.contains(entryZ), equalTo(false)),
            () -> assertThat(ids.contains(entryPublic), equalTo(false)));
    }

    // T18
    @Test
    void adminTestResultsHoldOnlyRowsOfOwnCompany() throws Exception {
        Company x = createCompany(uniqueBusinessId());
        String entryX = createSuccessfulEntryFor(x.businessId());
        String entryPublic = createSuccessfulEntryFor(Constants.PUBLIC_VALIDATION_TEST_ID);
        signInAsAdminOf("results-admin-" + UUID.randomUUID(), x);

        JsonNode body = testResults();

        assertAll(
            () -> assertThat(publicIds(body).contains(entryX), equalTo(true)),
            () -> assertThat(publicIds(body).contains(entryPublic), equalTo(false)),
            () -> assertThat(businessIds(body), contains(x.businessId())));
    }

    // T18
    @Test
    void userWithNoCompanyGetsPublicTestCompanyRowsInTestResults() throws Exception {
        Company other = createCompany(uniqueBusinessId());
        String entryPublic = createSuccessfulEntryFor(Constants.PUBLIC_VALIDATION_TEST_ID);
        String entryOther = createSuccessfulEntryFor(other.businessId());
        signInAsUserOf("results-nobody-" + UUID.randomUUID());

        List<String> ids = publicIds(testResults());

        assertAll(
            () -> assertThat(ids.contains(entryPublic), equalTo(true)),
            () -> assertThat(ids.contains(entryOther), equalTo(false)));
    }

    // T19
    @Test
    void loggedInSubmissionToOwnCompanyKeepsTheCompany() throws Exception {
        Company x = createCompany(uniqueBusinessId());
        signInAsUserOf("submit-user-" + UUID.randomUUID(), x);

        Entry stored = submitTestData(x.businessId());

        assertAll(
            () -> assertThat(stored.businessId(), equalTo(x.businessId())),
            () -> assertThat(stored.context(), nullValue()),
            () -> assertThat(stored.credentials(), nullValue()),
            () -> assertThat(stored.sendNotifications(), equalTo(false)),
            () -> assertThat(stored.notifications(), empty()));
    }

    // T20
    @Test
    void loggedInSubmissionToForeignCompanyGoesToPublicTestCompany() throws Exception {
        Company x = createCompany(uniqueBusinessId());
        Company y = createCompany(uniqueBusinessId());
        signInAsUserOf("submit-foreign-" + UUID.randomUUID(), x);

        Entry stored = submitTestData(y.businessId());

        assertThat(stored.businessId(), equalTo(Constants.PUBLIC_VALIDATION_TEST_ID));
    }

    @Test
    void adminSubmissionToAnyExistingCompanyKeepsTheCompany() throws Exception {
        Company x = createCompany(uniqueBusinessId());
        Company y = createCompany(uniqueBusinessId());
        signInAsAdminOf("submit-admin-" + UUID.randomUUID(), x);

        Entry stored = submitTestData(y.businessId());

        assertThat(stored.businessId(), equalTo(y.businessId()));
    }

    // T16
    @Test
    void anonymousTestDataEntrySendsNoNotifications() throws Exception {
        CreateEntryRequest request = richRequest("https://example.fi/notify.zip", null)
            .sendNotifications(true)
            .notifications(List.of("someone@example.fi"))
            .build();

        MvcResult result = apiCall(post("/ui/queue").content(toJson(request)))
            .andExpect(status().isOk())
            .andReturn();
        Entry stored = entryService.findEntry(apiResponse(result).get(DATA).get(PUBLIC_ID).stringValue()).orElseThrow();

        assertAll(
            () -> assertThat(stored.sendNotifications(), equalTo(false)),
            () -> assertThat(stored.notifications(), empty()));
    }

    // T10
    @Test
    void anonymousCanListRulesOfPublicTestCompanyOnly() throws Exception {
        apiCall(get("/ui/rules").param("businessId", Constants.PUBLIC_VALIDATION_TEST_ID)).andExpect(status().isOk());
        apiCall(get("/ui/rules").param("businessId", Constants.FINTRAFFIC_BUSINESS_ID)).andExpect(status().isUnauthorized());
    }

    // T14
    @Test
    void anonymousStateDoesNotLeakUrlQuotedInFindingMessage() throws Exception {
        String url = "https://nonexistent.invalid/feed?" + SECRET_QUERY;
        ImmutableEntry toCreate = TestObjects.anEntry("gtfs").url(url).build();
        EntryRecord entry = entryRepository.create(toCreate, Optional.empty(), Optional.empty()).orElseThrow();
        taskRepository.createTasks(entry, List.of(ImmutableTask.of(DownloadRule.PREPARE_DOWNLOAD_TASK, 1)));
        Task task = taskRepository.findTask(entry.id(), DownloadRule.PREPARE_DOWNLOAD_TASK).orElseThrow();
        Ruleset rule = recordMapper.toRuleset(rulesetRepository.findByName(RuleName.GTFS_CANONICAL).orElseThrow());
        findingRepository.create(TestObjects.aFinding(rule.id(), task.id())
            .severity(FindingSeverity.ERROR)
            .message("Failed to download " + url)
            .source(DownloadRule.PREPARE_DOWNLOAD_TASK)
            .build());

        MvcResult result = apiCall(get(entryStatePath(entry.publicId()))).andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getContentAsString(), not(containsString(SECRET_QUERY)));
    }
}
