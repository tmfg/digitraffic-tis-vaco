package fi.digitraffic.tis.vaco.caching;

import com.fasterxml.jackson.core.type.TypeReference;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.caching.model.CacheSummaryStatistics;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CachingControllerIntegrationTests extends SpringBootIntegrationTestBase {
    TypeReference<Resource<Map<String, CacheSummaryStatistics>>> listCacheStatsType = new TypeReference<>() {};

    @Test
    void canListCacheSummaryStatistics() throws Exception {
        MvcResult response = apiCall(get("/admin/caching/statistics"))
            .andExpect(status().isOk())
            .andReturn();
        Map<String, CacheSummaryStatistics> stats = apiResponse(response, listCacheStatsType).data();

        assertThat(stats.keySet(),
            equalTo(Set.of("rulesets",
                "SQS queue URLs",
                "local temporary file paths",
                "entries",
                "statuses",
                "classpath resources",
                "UI/MyData summaries",
                "DB/context records")));
    }

}
