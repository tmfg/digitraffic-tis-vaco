package fi.digitraffic.tis.vaco.featureflags;

import com.fasterxml.jackson.core.type.TypeReference;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.featureflags.model.FeatureFlag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeatureFlagsControllerIntegrationTests extends SpringBootIntegrationTestBase {
    TypeReference<Resource<List<FeatureFlag>>> listFeatureFlagsType = new TypeReference<>() {};
    TypeReference<Resource<FeatureFlag>> featureFlagType = new TypeReference<>() {};

    @Autowired
    FeatureFlagsService featureFlagsService;

    @Test
    void canListAllFeatureFlags() throws Exception {
        MvcResult response = apiCall(get("/feature-flags"))
            .andExpect(status().isOk())
            .andReturn();
        List<FeatureFlag> featureFlags = apiResponse(response, listFeatureFlagsType).data();

        assertThat(
            Streams.map(featureFlags, FeatureFlag::name).toSet(),
            equalTo(Set.of("emails.entryCompleteEmail", "emails.feedStatusEmail", "scheduledTasks.oldDataCleanup", "tasks.prepareDownload.skipDownloadOnStaleETag")));
    }

    @Test
    void canEnableAndDisableFlag() throws Exception {
        MvcResult enableResponse = apiCall(post("/feature-flags/emails.feedStatusEmail/enable"))
            .andExpect(status().isOk())
            .andReturn();
        assertThat(apiResponse(enableResponse, featureFlagType).data().enabled(), equalTo(true));
        assertThat(featureFlagsService.isFeatureFlagEnabled("emails.feedStatusEmail"), equalTo(true));

        MvcResult disableResponse = apiCall(post("/feature-flags/emails.feedStatusEmail/disable"))
            .andExpect(status().isOk())
            .andReturn();
        assertThat(apiResponse(disableResponse, featureFlagType).data().enabled(), equalTo(false));
        assertThat(featureFlagsService.isFeatureFlagEnabled("emails.feedStatusEmail"), equalTo(false));

        MvcResult enableAgainResponse = apiCall(post("/feature-flags/emails.feedStatusEmail/enable"))
            .andExpect(status().isOk())
            .andReturn();
        FeatureFlag reEnabled = apiResponse(enableAgainResponse, featureFlagType).data();
        assertThat(reEnabled.enabled(), equalTo(true));
        assertThat(featureFlagsService.isFeatureFlagEnabled("emails.feedStatusEmail"), equalTo(true));

        MvcResult response = apiCall(get("/feature-flags"))
            .andExpect(status().isOk())
            .andReturn();
        List<FeatureFlag> featureFlags = apiResponse(response, listFeatureFlagsType).data();
        Optional<FeatureFlag> feedStatusEmailFlag = Streams.filter(featureFlags, FeatureFlag::enabled).findFirst();

        assertAll(
            () -> assertThat(feedStatusEmailFlag.isPresent(), equalTo(true)),
            () -> assertThat(feedStatusEmailFlag.get(), equalTo(reEnabled))
        );
    }
}
