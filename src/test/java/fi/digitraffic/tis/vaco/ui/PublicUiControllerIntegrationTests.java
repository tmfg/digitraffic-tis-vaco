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
import fi.digitraffic.tis.vaco.findings.model.FindingSeverity;
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
 * <p>The URL rules are covered in IssuerRoutingAuthenticationTests, because this context has the filters switched off.
 */
class PublicUiControllerIntegrationTests extends SpringBootIntegrationTestBase {

    private static final String PUBLIC_ID = "publicId";
    private static final String DATA = "data";
    private static final String SECRET_QUERY = "token=secret";
    private static final String ADMIN_OID = "Admin Adminson";
    private static final String RESULT = "result";

    private final ObjectMapper plainMapper = new ObjectMapper();

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
        MvcResult result = mockMvc.perform(get("/ui/data-delivery/export").param("language", "fi")).andReturn();
        result.getAsyncResult(10_000);
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private JsonNode entryState(String publicId) throws Exception {
        MvcResult result = apiCall(get("/ui/entries/" + publicId + "/state"))
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
    void anonymousExportHasUrlWithoutQueryString() throws Exception {
        String url = uniqueUrl();
        createVisibleEntry(url, null);

        String csv = exportCsv();

        assertAll(
            () -> assertThat(csv, containsString(url.substring(0, url.indexOf('?')))),
            () -> assertThat(csv, not(containsString(SECRET_QUERY))));
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

        MvcResult result = apiCall(get("/ui/pages/company-entries/" + Constants.FINTRAFFIC_BUSINESS_ID))
            .andExpect(status().isOk())
            .andReturn();

        List<String> ids = new java.util.ArrayList<>();
        apiResponse(result).get(DATA).get("entries").forEach(e -> ids.add(e.get(PUBLIC_ID).stringValue()));
        assertThat(ids.contains(id), equalTo(true));
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

        MvcResult result = apiCall(get("/ui/entries/" + id + "/state")).andExpect(status().isOk()).andReturn();

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

        apiCall(get("/ui/entries/" + id + "/state").param("magic", "garbage")).andExpect(status().isOk());
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

        MvcResult result = apiCall(get("/ui/entries/" + entry.publicId() + "/state")).andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getContentAsString(), not(containsString(SECRET_QUERY)));
    }
}
