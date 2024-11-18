package fi.digitraffic.tis.vaco.feeds;

import com.fasterxml.jackson.databind.JsonNode;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.api.model.feed.ImmutableCreateFeedRequest;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class FeedControllerSystemTests extends SpringBootIntegrationTestBase {

    @BeforeAll
    static void beforeAll() {
        createQueue("vaco-errors");
        createQueue("rules-results");
        createQueue("rules-processing-gtfs-canonical");
        createQueue("DLQ-rules-processing");
    }

    @BeforeAll
    static void beforeAll(@Autowired VacoProperties vacoProperties) {
        CreateBucketResponse r = createBucket(vacoProperties.s3ProcessingBucket());
    }

    @Test
    void canCreateFeedModifyAndFetchFeeds() throws Exception {

        ImmutableCreateFeedRequest feed = TestObjects.aCreateFeedRequest().build();

        MvcResult response = apiCall(post("/v1/feeds").content(toJson(feed)))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode createResult = apiResponse(response);

        assertNotNull(createResult);

        MvcResult fetchResponse = apiCall(get("/v1/feeds")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode fetchResult = apiResponse(fetchResponse);

        assertEquals(fetchResult.size(), 1);
        assertEquals(feed.owner(), fetchResult.get(0).get("owner").get("businessId").asText());
        assertTrue(feed.processingEnabled());

        MvcResult modifyEnableProcessing  = apiCall(post("/v1/feeds/"+ fetchResult.get(0).get("publicId").asText()).param("enableProcessing", "false"))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode modifyEnableProcessingResponse = apiResponse(modifyEnableProcessing);
        assertEquals(modifyEnableProcessingResponse.get("data").get("processingEnabled").asText(), "false");

        apiCall(delete("/v1/feeds/" + fetchResult.get(0).get("publicId").asText()))
                .andExpect(status().isNoContent());

        MvcResult fetchAfterDeleteResponse = apiCall(get("/v1/feeds")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode fetchAfterDeleteResult = apiResponse(fetchAfterDeleteResponse);
        assertEquals(0, fetchAfterDeleteResult.size());
    }
}
