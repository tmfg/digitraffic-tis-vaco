package fi.digitraffic.tis.vaco.ui;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.api.model.credentials.ImmutableCreateCredentialsRequest;
import fi.digitraffic.tis.vaco.api.model.queue.CreateEntryRequest;
import fi.digitraffic.tis.vaco.api.model.queue.ImmutableCreateEntryRequest;
import fi.digitraffic.tis.vaco.credentials.CredentialsService;
import fi.digitraffic.tis.vaco.credentials.model.CredentialsType;
import fi.digitraffic.tis.vaco.credentials.model.ImmutableHttpBasicAuthenticationDetails;
import fi.digitraffic.tis.vaco.crypt.EncryptionService;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
import fi.digitraffic.tis.vaco.db.repositories.FindingRepository;
import fi.digitraffic.tis.vaco.db.repositories.RulesetRepository;
import fi.digitraffic.tis.vaco.db.repositories.TaskRepository;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.findings.model.FindingSeverity;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ui.model.ImmutableMagicToken;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UiControllerIntegrationTests extends SpringBootIntegrationTestBase {

    private static final String COMPANIES_PATH = "/ui/admin/companies";

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private JdbcTemplate jdbc;

    private void signInAsAdmin() {
        String oid = "Admin Adminson";
        SecurityContextHolder.getContext().setAuthentication(TestObjects.jwtAdminAuthenticationToken(oid));
        injectAuthOverrides(oid, asFintrafficIdGroup(companyHierarchyService.findByBusinessId("2942108-7").get()));
    }

    private static String newBusinessId() {
        int[] weights = {7, 9, 10, 5, 8, 4, 2};
        while (true) {
            String base = String.format("%07d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999));
            int sum = 0;
            for (int i = 0; i < 7; i++) {
                sum += (base.charAt(i) - '0') * weights[i];
            }
            int remainder = sum % 11;
            if (remainder != 1) {
                return base + "-" + (remainder == 0 ? 0 : 11 - remainder);
            }
        }
    }

    @Test
    void adminCanAddCompany() throws Exception {
        signInAsAdmin();
        String id = newBusinessId();

        MvcResult result = apiCall(post(COMPANIES_PATH).content(toJson(ImmutableCompany.of(id, "Kuljetus Lakkapää Oy", true))))
            .andExpect(status().isOk()).andReturn();

        assertThat(apiResponse(result).get("data").get("businessId").stringValue(), equalTo(id));
    }

    @Test
    void addingDuplicateCompanyIsConflict() throws Exception {
        signInAsAdmin();
        String id = newBusinessId();
        apiCall(post(COMPANIES_PATH).content(toJson(ImmutableCompany.of(id, "Twin", true)))).andExpect(status().isOk());

        apiCall(post(COMPANIES_PATH).content(toJson(ImmutableCompany.of(id, "Twin", true)))).andExpect(status().isConflict());
    }

    @Test
    void addingCompanyWithBadBusinessIdOrBlankNameIsBadRequest() throws Exception {
        signInAsAdmin();

        apiCall(post(COMPANIES_PATH).content(toJson(ImmutableCompany.of("1234567", "No check digit", true))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0]", startsWith("Field 'businessId'")));
        apiCall(post(COMPANIES_PATH).content(toJson(ImmutableCompany.of("2499374-9", "Wrong check digit", true))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0]", startsWith("Field 'businessId'")));
        apiCall(post(COMPANIES_PATH).content(toJson(ImmutableCompany.of(newBusinessId(), " ", true))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0]", startsWith("Field 'name'")));
    }

    @Test
    void adminCanDeleteCompanyWithLinkedData() throws Exception {
        signInAsAdmin();
        String id = newBusinessId();
        apiCall(post(COMPANIES_PATH).content(toJson(ImmutableCompany.of(id, "Short lived", true)))).andExpect(status().isOk());
        jdbc.update("INSERT INTO entry(format, url, business_id, status) VALUES ('gtfs', 'http://x', ?, 'success')", id);

        apiCall(delete(COMPANIES_PATH + "/" + id)).andExpect(status().isNoContent());

        assertThat(companyHierarchyService.findByBusinessId(id).isPresent(), equalTo(false));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM entry WHERE business_id = ?", Long.class, id), equalTo(0L));
    }

    @Test
    void adminCanCountLinkedData() throws Exception {
        signInAsAdmin();
        String id = newBusinessId();
        apiCall(post(COMPANIES_PATH).content(toJson(ImmutableCompany.of(id, "Has data", true)))).andExpect(status().isOk());
        jdbc.update("INSERT INTO entry(format, url, business_id, status) VALUES ('gtfs', 'http://x', ?, 'success')", id);

        apiCall(get(COMPANIES_PATH + "/" + id + "/references"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.entries").value(1));
        apiCall(get(COMPANIES_PATH + "/" + newBusinessId() + "/references")).andExpect(status().isNotFound());
    }

    @Test
    void deletingMissingCompanyIsNotFound() throws Exception {
        signInAsAdmin();

        apiCall(delete(COMPANIES_PATH + "/" + newBusinessId())).andExpect(status().isNotFound());
    }

    @Test
    void deletingCompanyWithUnfinishedEntryIsConflictWithReason() throws Exception {
        signInAsAdmin();
        String id = newBusinessId();
        apiCall(post(COMPANIES_PATH).content(toJson(ImmutableCompany.of(id, "Busy", true)))).andExpect(status().isOk());
        jdbc.update("INSERT INTO entry(format, url, business_id, status) VALUES ('gtfs', 'http://x', ?, 'processing')", id);

        apiCall(delete(COMPANIES_PATH + "/" + id))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.data").value("IN_PROGRESS"));

        assertThat(companyHierarchyService.findByBusinessId(id).isPresent(), equalTo(true));
    }

    @Test
    void deletingProtectedCompanyIsConflictWithReason() throws Exception {
        signInAsAdmin();

        apiCall(delete(COMPANIES_PATH + "/" + Constants.FINTRAFFIC_BUSINESS_ID))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.data").value("PROTECTED"));
    }

    @Test
    void canFetchEntryStateWithPublicId() throws Exception {
        CreateEntryRequest request = TestObjects.aValidationEntryRequest().build();
        String oid = "Joh Rnado";
        JwtAuthenticationToken johRnado = TestObjects.jwtAuthenticationToken(oid);
        SecurityContextHolder.getContext().setAuthentication(johRnado);
        injectAuthOverrides(oid, asFintrafficIdGroup(companyHierarchyService.findByBusinessId(request.businessId()).get()));

        MvcResult response = apiCall(post("/queue").content(toJson(request)))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode createResult = apiResponse(response);
        String entryPublicId = createResult.get("data").get("publicId").stringValue();

        MvcResult fetchResponse = apiCall(get("/ui/entries/" + entryPublicId + "/state"))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode fetchResult = apiResponse(fetchResponse);

        assertAll("Entry fields are fetched properly",
            () -> assertThat(fetchResult.get("data").get("entry").get("data").get("name").stringValue(), equalTo(request.name())),
            () -> assertThat(fetchResult.get("data").get("entry").get("data").get("url").stringValue(), equalTo(request.url())),
            () -> assertThat(fetchResult.get("data").get("entry").get("data").get("etag").stringValue(), equalTo(request.etag())),
            () -> assertThat(fetchResult.get("data").get("entry").get("data").get("format").stringValue(), equalTo(request.format())));
    }

    private static final String RERUN_PATH_TEMPLATE = "/ui/admin/entries/%s/rerun";

    private static final Set<String> RESULTS_PAGE_ENTRY_FIELDS = Set.of(
        "publicId", "name", "format", "url", "etag", "context", "created", "completed", "tasks", "validations", "conversions");

    private final ObjectMapper plainMapper = new ObjectMapper();

    @Autowired
    private CredentialsService credentialsService;

    @Autowired
    private EntryService entryService;

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

    private static Set<String> fieldsOf(JsonNode node) {
        return new TreeSet<>(node.propertyNames());
    }

    private JsonNode createEntryAsAdmin(CreateEntryRequest request) throws Exception {
        signInAsAdmin();
        MvcResult response = apiCall(post("/queue").content(toJson(request)))
            .andExpect(status().isOk())
            .andReturn();
        return apiResponse(response).get("data");
    }

    private ImmutableCreateEntryRequest.Builder aRichEntryRequest(String credentialsPublicId) {
        ObjectNode metadata = plainMapper.createObjectNode()
            .put("caller", "FINAP")
            .put("operator-name", "Rerun Test Oy");
        ObjectNode validation = plainMapper.createObjectNode().put("name", RuleName.GTFS_CANONICAL);
        validation.putObject("config");
        ObjectNode conversion = plainMapper.createObjectNode().put("name", RuleName.GTFS2NETEX_FINTRAFFIC);
        conversion.putObject("config");
        return TestObjects.aValidationEntryRequest()
            .name("rerun source")
            .etag("etag-1")
            .context("rerun-context")
            .credentials(credentialsPublicId)
            .sendNotifications(false)
            .metadata(metadata)
            .notifications(List.of("someone@example.fi"))
            .validations(List.of(validation))
            .conversions(List.of(conversion));
    }

    private String createCredentialsForFintraffic() {
        // credentials names are unique per owner and the database is shared between tests
        return credentialsService.createCredentials(ImmutableCreateCredentialsRequest.of(
                CredentialsType.HTTP_BASIC,
                "rerun credentials " + UUID.randomUUID(),
                "testing credentials",
                Constants.FINTRAFFIC_BUSINESS_ID,
                ImmutableHttpBasicAuthenticationDetails.of("userId", "password")))
            .orElseThrow()
            .publicId();
    }

    @Test
    void entryStateReturnsOnlyShownEntryFields() throws Exception {
        JsonNode created = createEntryAsAdmin(aRichEntryRequest(createCredentialsForFintraffic()).build());

        MvcResult result = apiCall(get("/ui/entries/" + created.get("publicId").stringValue() + "/state"))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode entry = apiResponse(result).get("data").get("entry").get("data");

        assertAll("entry has only the fields that the results page shows",
            () -> assertThat(fieldsOf(entry), equalTo(new TreeSet<>(RESULTS_PAGE_ENTRY_FIELDS))),
            () -> assertThat(entry.get("tasks").size() > 0, equalTo(true)),
            () -> assertThat(fieldsOf(entry.get("tasks").get(0)), equalTo(new TreeSet<>(Set.of("name", "status", "completed")))),
            () -> assertThat(fieldsOf(entry.get("validations").get(0)), equalTo(new TreeSet<>(Set.of("name")))),
            () -> assertThat(fieldsOf(entry.get("conversions").get(0)), equalTo(new TreeSet<>(Set.of("name")))));
    }

    @Test
    void entryStateReturnsOnlyCredentialsName() throws Exception {
        JsonNode created = createEntryAsAdmin(aRichEntryRequest(createCredentialsForFintraffic()).build());

        MvcResult result = apiCall(get("/ui/entries/" + created.get("publicId").stringValue() + "/state"))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode credentials = apiResponse(result).get("data").get("credentials");

        assertAll("credentials has only the name",
            () -> assertThat(fieldsOf(credentials), equalTo(new TreeSet<>(Set.of("name")))),
            () -> assertThat(credentials.get("name").stringValue(), startsWith("rerun credentials ")));
    }

    @Test
    void entryStateWithoutCredentialsHasNullCredentials() throws Exception {
        JsonNode created = createEntryAsAdmin(TestObjects.aValidationEntryRequest().build());

        MvcResult result = apiCall(get("/ui/entries/" + created.get("publicId").stringValue() + "/state"))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(apiResponse(result).get("data").get("credentials").isNull(), equalTo(true));
    }

    @Test
    void entryStateReturnsOnlyRawForFindingItems() throws Exception {
        signInAsAdmin();
        ImmutableEntry toCreate = TestObjects.anEntry("gtfs").build();
        EntryRecord entry = entryRepository.create(toCreate, Optional.empty(), Optional.empty()).orElseThrow();
        taskRepository.createTasks(entry, List.of(ImmutableTask.of(RuleName.GTFS_CANONICAL, 1)));
        Task task = taskRepository.findTask(entry.id(), RuleName.GTFS_CANONICAL).orElseThrow();
        Ruleset rule = recordMapper.toRuleset(rulesetRepository.findByName(RuleName.GTFS_CANONICAL).orElseThrow());
        findingRepository.create(TestObjects.aFinding(rule.id(), task.id())
            .severity(FindingSeverity.ERROR)
            .message("some_finding")
            .source(RuleName.GTFS_CANONICAL)
            .raw("raw content".getBytes(StandardCharsets.UTF_8))
            .build());

        MvcResult result = apiCall(get("/ui/entries/" + entry.publicId() + "/state"))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode aggregated = apiResponse(result).get("data").get("reports").get(0).get("findings").get(0);

        assertAll("finding items have only raw, the aggregate stays",
            () -> assertThat(fieldsOf(aggregated), equalTo(new TreeSet<>(Set.of("code", "severity", "total", "findings")))),
            () -> assertThat(aggregated.get("findings").size(), equalTo(1)),
            () -> assertThat(fieldsOf(aggregated.get("findings").get(0)), equalTo(new TreeSet<>(Set.of("raw")))));
    }

    @Test
    void queueApiStillReturnsMetadataAndCredentials() throws Exception {
        String credentialsId = createCredentialsForFintraffic();
        JsonNode created = createEntryAsAdmin(aRichEntryRequest(credentialsId).build());

        MvcResult result = apiCall(get("/queue/" + created.get("publicId").stringValue()))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode entry = apiResponse(result).get("data");

        assertAll("the public queue API keeps the full entry",
            () -> assertThat(entry.get("metadata").get("caller").stringValue(), equalTo("FINAP")),
            () -> assertThat(entry.get("credentials").stringValue(), equalTo(credentialsId)));
    }

    @Test
    void rerunCopiesStoredEntryFields() throws Exception {
        String credentialsId = createCredentialsForFintraffic();
        JsonNode created = createEntryAsAdmin(aRichEntryRequest(credentialsId).build());
        String storedId = created.get("publicId").stringValue();

        MvcResult result = apiCall(post(RERUN_PATH_TEMPLATE.formatted(storedId)))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode rerun = apiResponse(result).get("data");

        MvcResult queueResult = apiCall(post("/ui/queue").content(toJson(TestObjects.aValidationEntryRequest().build())))
            .andExpect(status().isOk())
            .andReturn();
        Set<String> queueResponseFields = fieldsOf(apiResponse(queueResult).get("data"));

        Entry newEntry = entryService.findEntry(rerun.get("publicId").stringValue()).orElseThrow();
        assertAll("new entry copies the stored entry",
            () -> assertThat(rerun.get("publicId").stringValue(), not(equalTo(storedId))),
            () -> assertThat(newEntry.name(), equalTo("rerun source")),
            () -> assertThat(newEntry.businessId(), equalTo(Constants.FINTRAFFIC_BUSINESS_ID)),
            () -> assertThat(newEntry.url(), equalTo("https://example.fi")),
            () -> assertThat(newEntry.etag(), equalTo("etag-1")),
            () -> assertThat(newEntry.context(), equalTo("rerun-context")),
            () -> assertThat(newEntry.format(), equalTo("gtfs")),
            () -> assertThat(newEntry.sendNotifications(), equalTo(false)),
            () -> assertThat(newEntry.credentials(), equalTo(credentialsId)),
            () -> assertThat(newEntry.validations().stream().map(ValidationInput::name).toList(), equalTo(List.of(RuleName.GTFS_CANONICAL))),
            () -> assertThat(newEntry.validations().get(0).config(), notNullValue()),
            () -> assertThat(newEntry.conversions().stream().map(ConversionInput::name).toList(), equalTo(List.of(RuleName.GTFS2NETEX_FINTRAFFIC))),
            () -> assertThat(newEntry.conversions().get(0).config(), notNullValue()),
            () -> assertThat(fieldsOf(rerun), equalTo(queueResponseFields)));
    }

    @Test
    void rerunDropsMetadataAndNotificationEmails() throws Exception {
        JsonNode created = createEntryAsAdmin(aRichEntryRequest(null).build());
        String storedId = created.get("publicId").stringValue();
        assertThat("the stored entry has metadata", entryService.findEntry(storedId).orElseThrow().metadata(), notNullValue());

        MvcResult result = apiCall(post(RERUN_PATH_TEMPLATE.formatted(storedId)))
            .andExpect(status().isOk())
            .andReturn();
        Entry newEntry = entryService.findEntry(apiResponse(result).get("data").get("publicId").stringValue()).orElseThrow();

        assertAll("new entry does not carry the metadata or the e-mails",
            () -> assertThat(newEntry.metadata(), nullValue()),
            () -> assertThat(newEntry.notifications(), empty()));
    }

    @Test
    void rerunUnknownEntryIs404() throws Exception {
        signInAsAdmin();

        apiCall(post(RERUN_PATH_TEMPLATE.formatted("does-not-exist"))).andExpect(status().isNotFound());
    }

    // Method security is off in this test context and the filter chain is switched off, so the endpoint's
    // guard is asserted on the handler itself. The URL rules are covered in IssuerRoutingAuthenticationTests.
    @Test
    void rerunHandlerRequiresAdminAuthority() {
        Method handler = Arrays.stream(UiController.class.getDeclaredMethods())
            .filter(m -> m.isAnnotationPresent(PostMapping.class))
            .filter(m -> Arrays.asList(m.getAnnotation(PostMapping.class).path()).contains("/admin/entries/{publicId}/rerun"))
            .findFirst()
            .orElseThrow();

        assertThat(handler.getAnnotation(PreAuthorize.class).value(), equalTo("hasAuthority('vaco.admin')"));
    }

    @Test
    void dataDeliveryOmitsFeedName() throws Exception {
        createEntryAsAdmin(TestObjects.aValidationEntryRequest().build());

        MvcResult result = apiCall(get("/ui/admin/data-delivery"))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode body = apiResponse(result);

        assertThat(body.size() > 0, equalTo(true));
        body.forEach(item -> assertThat(fieldsOf(item.get("data")), not(hasItem("feedName"))));
    }

    @Test
    void adminEntriesListIsRemoved() throws Exception {
        signInAsAdmin();

        apiCall(get("/ui/admin/entries").param("businessId", Constants.FINTRAFFIC_BUSINESS_ID))
            .andExpect(status().isNotFound());
    }

    @Test
    void returnsError404OnNonExistingId() throws Exception {
        apiCall(get("/ui/entries/smth/state"))
            .andExpect(status().isNotFound())
            .andReturn();
    }

    // A corrupted magic token must degrade to a 404 like a denied/absent magic link, not crash
    // the request with an uncaught decrypt() exception (500).
    @Test
    void fetchEntryStateDoesNotReturn500WhenMagicTokenDecryptFails() throws Exception {
        CreateEntryRequest request = TestObjects.aValidationEntryRequest().build();
        String oid = "Joh Rnado";
        JwtAuthenticationToken johRnado = TestObjects.jwtAuthenticationToken(oid);
        SecurityContextHolder.getContext().setAuthentication(johRnado);
        injectAuthOverrides(oid, asFintrafficIdGroup(companyHierarchyService.findByBusinessId(request.businessId()).get()));

        MvcResult response = apiCall(post("/queue").content(toJson(request)))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode createResult = apiResponse(response);
        String entryPublicId = createResult.get("data").get("publicId").stringValue();

        String corruptedMagicToken = corruptMagicToken(entryPublicId);

        // requester is otherwise unauthenticated -- only the magic-link path is under test
        SecurityContextHolder.clearContext();

        apiCall(get("/ui/entries/" + entryPublicId + "/state?magic=" + corruptedMagicToken))
            .andExpect(status().isNotFound())
            .andReturn();
    }

    // A `magic` value that isn't even valid base64 must also degrade to 404, not 500.
    @Test
    void fetchEntryStateDoesNotReturn500WhenMagicTokenIsGarbage() throws Exception {
        CreateEntryRequest request = TestObjects.aValidationEntryRequest().build();
        String oid = "Joh Rnado";
        JwtAuthenticationToken johRnado = TestObjects.jwtAuthenticationToken(oid);
        SecurityContextHolder.getContext().setAuthentication(johRnado);
        injectAuthOverrides(oid, asFintrafficIdGroup(companyHierarchyService.findByBusinessId(request.businessId()).get()));

        MvcResult response = apiCall(post("/queue").content(toJson(request)))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode createResult = apiResponse(response);
        String entryPublicId = createResult.get("data").get("publicId").stringValue();

        SecurityContextHolder.clearContext();

        apiCall(get("/ui/entries/" + entryPublicId + "/state?magic=not-a-valid-token-at-all!!!"))
            .andExpect(status().isNotFound())
            .andReturn();
    }

    /**
     * Encrypts a magic token, then flips a byte in its raw ciphertext (not the IV) so decrypting
     * it fails the GCM authentication tag check.
     */
    private String corruptMagicToken(String entryPublicId) {
        String token = encryptionService.encrypt(ImmutableMagicToken.of(entryPublicId));

        var decoder = Base64.getUrlDecoder();
        var encoder = Base64.getUrlEncoder();

        String combined = new String(decoder.decode(token), StandardCharsets.UTF_8);
        String[] split = combined.split("\\.");
        byte[] cypherText = decoder.decode(split[0]);
        String iv64 = split[1];

        cypherText[cypherText.length / 2] ^= (byte) 0xFF;

        String corruptedCombined = new String(encoder.encode(cypherText), StandardCharsets.UTF_8) + "." + iv64;
        return new String(encoder.encode(corruptedCombined.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }
}
