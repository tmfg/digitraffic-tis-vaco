package fi.digitraffic.tis.vaco.ui;

import com.fasterxml.jackson.databind.JsonNode;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.api.model.queue.CreateEntryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UiControllerIntegrationTests extends SpringBootIntegrationTestBase {

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
        String entryPublicId = createResult.get("data").get("publicId").textValue();

        MvcResult fetchResponse = apiCall(get("/ui/entries/" + entryPublicId + "/state"))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode fetchResult = apiResponse(fetchResponse);

        assertAll("Entry fields are fetched properly",
            () -> assertThat(fetchResult.get("data").get("entry").get("data").get("name").textValue(), equalTo(request.name())),
            () -> assertThat(fetchResult.get("data").get("entry").get("data").get("url").textValue(), equalTo(request.url())),
            () -> assertThat(fetchResult.get("data").get("entry").get("data").get("etag").textValue(), equalTo(request.etag())),
            () -> assertThat(fetchResult.get("data").get("entry").get("data").get("format").textValue(), equalTo(request.format())));
    }

    @Test
    void returnsError404OnNonExistingId() throws Exception {
        apiCall(get("/ui/entries/smth/state"))
            .andExpect(status().isNotFound())
            .andReturn();
    }
}
