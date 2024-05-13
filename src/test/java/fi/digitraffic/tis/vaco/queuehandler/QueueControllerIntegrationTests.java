package fi.digitraffic.tis.vaco.queuehandler;

import com.fasterxml.jackson.databind.JsonNode;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.api.model.Link;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.api.model.queue.CreateEntryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QueueControllerIntegrationTests extends SpringBootIntegrationTestBase {

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

}
