package fi.digitraffic.tis.vaco.caching;

import com.fasterxml.jackson.core.type.TypeReference;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.caching.model.CacheSummaryStatistics;
import fi.digitraffic.tis.vaco.caching.model.ImmutableCacheSummaryStatistics;
import fi.digitraffic.tis.vaco.caching.model.ImmutableEvictionStatistics;
import fi.digitraffic.tis.vaco.caching.model.ImmutableLoadStatistics;
import fi.digitraffic.tis.vaco.caching.model.ImmutableRequestStatistics;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

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

        CacheSummaryStatistics emptyStatistics = ImmutableCacheSummaryStatistics.of(
            true,
            0,
            ImmutableRequestStatistics.of(0, 0),
            ImmutableLoadStatistics.of(0, 0, 0, 0),
            ImmutableEvictionStatistics.of(0),
            List.of()
        );
        assertThat(stats,
            equalTo(Map.of("rulesets", emptyStatistics,
                "SQS queue URLs", emptyStatistics,
                "local temporary file paths", emptyStatistics,
                "entries", emptyStatistics,
                "statuses", emptyStatistics,
                "classpath resources", emptyStatistics,
                "UI/MyData summaries", emptyStatistics,
                "DB/context records", emptyStatistics)));
    }

}
