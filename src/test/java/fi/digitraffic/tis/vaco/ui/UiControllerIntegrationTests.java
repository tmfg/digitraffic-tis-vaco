package fi.digitraffic.tis.vaco.ui;

import com.fasterxml.jackson.databind.JsonNode;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.queuehandler.dto.EntryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class UiControllerIntegrationTests extends SpringBootIntegrationTestBase {

    @Test
    void canFetchEntryStateWithPublicId() throws Exception {
        EntryRequest request = TestObjects.aValidationEntryRequest().build();
        MvcResult response = apiCall(post("/queue").content(toJson(request)))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode createResult = apiResponse(response);
        String entryPublicId = createResult.get("data").get("publicId").textValue();

        MvcResult fetchResponse = apiCall(get("/ui/entry/" + entryPublicId + "/state"))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode fetchResult = apiResponse(fetchResponse);

        assertAll("Entry fields are fetched properly",
            () -> assertThat(fetchResult.get("data").get("entry").get("name").textValue(), equalTo(request.getName())),
            () -> assertThat(fetchResult.get("data").get("entry").get("url").textValue(), equalTo(request.getUrl())),
            () -> assertThat(fetchResult.get("data").get("entry").get("etag").textValue(), equalTo(request.getEtag())),
            () -> assertThat(fetchResult.get("data").get("entry").get("format").textValue(), equalTo(request.getFormat())));
    }

    @Test
    void returnsError404OnNonExistingId() throws Exception {
        MvcResult fetchResponse = apiCall(get("/ui/entry/smth/state"))
            .andExpect(status().isNotFound())
            .andReturn();
    }
}
