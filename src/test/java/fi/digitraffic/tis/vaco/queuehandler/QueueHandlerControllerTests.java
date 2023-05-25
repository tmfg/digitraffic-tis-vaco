package fi.digitraffic.tis.vaco.queuehandler;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.queuehandler.dto.entry.EntryCommand;
import fi.digitraffic.tis.vaco.queuehandler.dto.entry.EntryResult;
import fi.digitraffic.tis.vaco.queuehandler.dto.entry.EntryResultResource;
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
        EntryCommand command = new EntryCommand("format", "url", null, null, new EntryCommand.Validation(), new EntryCommand.Conversion());
        MvcResult response = apiCall(post("/queue").content(toJson(command)))
                .andExpect(status().isOk())
                .andReturn();
        var createResult = apiResponse(response, EntryResultResource.class);

        // follow the self-reference link from previous response
        MvcResult fetchResponse = apiCall(createResult.links().get("self"))
                .andExpect(status().isOk())
                .andReturn();
        var fetchResult = apiResponse(fetchResponse, EntryResult.class);

        // assert provided data has stayed the same
        assertAll("Base fields are stored properly",
                () -> assertThat(fetchResult.entry().url(), equalTo(command.url())),
                () -> assertThat(fetchResult.entry().etag(), equalTo(command.etag())),
                () -> assertThat(fetchResult.entry().format(), equalTo(command.format())));
    }
}
