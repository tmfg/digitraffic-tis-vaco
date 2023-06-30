package fi.digitraffic.tis.vaco.queuehandler;

import com.fasterxml.jackson.core.type.TypeReference;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.queuehandler.dto.EntryCommand;
import fi.digitraffic.tis.vaco.queuehandler.dto.QueueHandlerResource;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QueueHandlerControllerTests extends SpringBootIntegrationTestBase {

    private final TypeReference<QueueHandlerResource<ImmutableEntry>> queueHandlerResource = new TypeReference<>() {};

    @Test
    void canCreateEntryAndFetchItsDetailsWithPublicId() throws Exception {
        // create new entry to queue
        EntryCommand command = TestObjects.aValidationEntryRequest().build();
        MvcResult response = apiCall(post("/queue").content(toJson(command)))
            .andExpect(status().isOk())
            .andReturn();
        QueueHandlerResource<ImmutableEntry> createResult = apiResponse(response, queueHandlerResource);

        // follow the self-reference link from previous response
        MvcResult fetchResponse = apiCall(createResult.links().get("self"))
            .andExpect(status().isOk())
            .andReturn();
        var fetchResult = apiResponse(fetchResponse, queueHandlerResource);

        // assert provided data has stayed the same
        assertAll("Base fields are stored properly",
            () -> assertThat(fetchResult.data().url(), equalTo(command.getUrl())),
            () -> assertThat(fetchResult.data().etag(), equalTo(command.getEtag())),
            () -> assertThat(fetchResult.data().format(), equalTo(command.getFormat())));

        assertThat("API endpoints should not expose internal IDs.", fetchResult.data().id(), is(nullValue()));
    }
}
