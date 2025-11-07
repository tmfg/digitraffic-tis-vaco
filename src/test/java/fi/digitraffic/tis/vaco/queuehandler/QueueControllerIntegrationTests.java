package fi.digitraffic.tis.vaco.queuehandler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestConstants;
import fi.digitraffic.tis.vaco.api.model.Link;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.api.model.queue.CreateEntryRequest;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QueueControllerIntegrationTests extends SpringBootIntegrationTestBase {

    private final TypeReference<List<Resource<Entry>>> listEntriesResourceType = new TypeReference<>() {};

    @Autowired
    private EntryService entryService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void canCreateEntryAndFetchItsDetailsWithPublicId() throws Exception {
        // create new entry to queue
        CreateEntryRequest request = TestObjects.aValidationEntryRequest().build();
        String oid = "Rohn Mnadoj";
        JwtAuthenticationToken rohnMnadoj = TestObjects.jwtAuthenticationToken(oid);
        SecurityContextHolder.getContext().setAuthentication(rohnMnadoj);
        injectAuthOverrides(oid, asFintrafficIdGroup(companyHierarchyService.findByBusinessId(request.businessId()).get()));

        MvcResult response = apiCall(post("/queue").content(toJson(request)))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode createResult = apiResponse(response);

        Link self = toLink((createResult.get("links").get("refs").get("self")));

        // follow the self-reference link from previous response
        MvcResult fetchResponse = apiCall(self)
            .andExpect(status().isOk())
            .andReturn();
        JsonNode fetchResult = apiResponse(fetchResponse);

        // assert provided data has stayed the same
        assertAll("Base fields are stored properly",
            () -> assertThat(fetchResult.get("data").get("name").textValue(), equalTo(request.name())),
            () -> assertThat(fetchResult.get("data").get("url").textValue(), equalTo(request.url())),
            () -> assertThat(fetchResult.get("data").get("etag").textValue(), equalTo(request.etag())),
            () -> assertThat(fetchResult.get("data").get("format").textValue(), equalTo(request.format())));

        // allow anonymous linking to resource
        Link magic = toLink((fetchResult.get("links").get("refs").get("magic")));
        assertThat(magic.href(), startsWith("http://localhost:8080/ui/data/" + fetchResult.get("data").get("publicId").textValue() + "?magic="));

        assertThat("API endpoints should not expose internal IDs.", fetchResult.get("data").has("id"), equalTo(false));
    }

    private List<CreateEntryRequest> generateTestEntries(int count, String name) {
        List<CreateEntryRequest> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entries.add(TestObjects.aValidationEntryRequest().name(name).build());
        }
        return entries;
    }

    private void createEntry(CreateEntryRequest request) throws Exception {
        String oid = "Test User";
        JwtAuthenticationToken authToken = TestObjects.jwtAuthenticationToken(oid);
        SecurityContextHolder.getContext().setAuthentication(authToken);
        injectAuthOverrides(oid, asFintrafficIdGroup(companyHierarchyService.findByBusinessId(request.businessId()).get()));

        apiCall(post("/queue").content(toJson(request)))
            .andExpect(status().isOk())
            .andReturn();
    }

    private List<Resource<Entry>> fetchEntries(String businessId, Integer count, String name) throws Exception {
        StringBuilder urlBuilder = new StringBuilder("/queue?businessId=" + businessId);
        if (count != null) {
            urlBuilder.append("&count=").append(count);
        }
        if (name != null) {
            urlBuilder.append("&name=").append(name);
        }

        MvcResult response = apiCall(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(urlBuilder.toString()))
            .andExpect(status().isOk())
            .andReturn();
        return apiResponse(response, listEntriesResourceType);
    }

    @Test
    void getQueueEntries() throws Exception {
        List<CreateEntryRequest> testEntries = generateTestEntries(5, "Test Entry A");
        testEntries.addAll(generateTestEntries(10, "Test Entry B"));

        for (CreateEntryRequest entry : testEntries) {
            try {
                createEntry(entry);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        String businessId = testEntries.stream().findFirst().get().businessId();
        List<Resource<Entry>> entries = fetchEntries(businessId, 3, "Test Entry A");
        assertThat("Should return only requested number of entries.", entries.size(), equalTo(3));
        for (Resource<Entry> entry : entries) {
            assertThat("Should return only entries matching the name filter.", entry.data().name(), equalTo("Test Entry A"));
        }


        entries = fetchEntries(businessId, null, "Test Entry B");
        assertThat("Should return all entries matching the name filter.", entries.size(), equalTo(10));
        for (Resource<Entry> entry : entries) {
            assertThat("Should return only entries matching the name filter.", entry.data().name(), equalTo("Test Entry B"));
        }

        entries = fetchEntries(businessId, 7, null);
        assertThat("Should return only requested number of entries.", entries.size(), equalTo(7));

        entries = fetchEntries(businessId, null, null);
        assertThat("Should return all entries for the business ID.", entries.size(), equalTo(15));
    }

    @Test
    void getQueueEntriesBadRequest() throws Exception {
        String businessId = TestObjects.aValidationEntryRequest().build().businessId();
        apiCall(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/queue?businessId="+businessId+"&count=-1"))
            .andExpect(status().isBadRequest())
            .andReturn();

        apiCall(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/queue?businessId="+businessId+"&count=0"))
            .andExpect(status().isBadRequest())
            .andReturn();

        apiCall(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/queue?businessId="+businessId+"&name= "))
            .andExpect(status().isBadRequest())
            .andReturn();
    }

    @AfterEach
    void tearDown() {
        entryService.findAllByBusinessId(TestConstants.FINTRAFFIC_BUSINESS_ID)
            .forEach(e -> jdbc.update("DELETE FROM entry WHERE public_id = ?", e.publicId()));
    }
}
