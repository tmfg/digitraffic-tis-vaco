package fi.digitraffic.tis.vaco.queuehandler;

import com.fasterxml.jackson.databind.JsonNode;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.utilities.dto.Link;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.queuehandler.dto.EntryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QueueHandlerControllerTests extends SpringBootIntegrationTestBase {

    @Test
    void canCreateEntryAndFetchItsDetailsWithPublicId() throws Exception {
        // create new entry to queue
        EntryRequest request = TestObjects.aValidationEntryRequest().build();
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
            () -> assertThat(fetchResult.get("data").get("name").textValue(), equalTo(request.getName())),
            () -> assertThat(fetchResult.get("data").get("url").textValue(), equalTo(request.getUrl())),
            () -> assertThat(fetchResult.get("data").get("etag").textValue(), equalTo(request.getEtag())),
            () -> assertThat(fetchResult.get("data").get("format").textValue(), equalTo(request.getFormat())));

        assertThat("API endpoints should not expose internal IDs.", fetchResult.get("data").has("id"), equalTo(false));
    }

}
