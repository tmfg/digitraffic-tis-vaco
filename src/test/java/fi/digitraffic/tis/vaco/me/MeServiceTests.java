package fi.digitraffic.tis.vaco.me;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.admintasks.AdminTasksService;
import fi.digitraffic.tis.vaco.admintasks.model.GroupIdMappingTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class MeServiceTests extends SpringBootIntegrationTestBase {

    @Autowired
    private MeService meService;

    @Autowired
    private AdminTasksService adminTasksService;

    @Test
    void resolvingCompaniesAutoregistersUnknownGroupIds() {
        JwtAuthenticationToken token = TestObjects.jwtAuthenticationToken("Jornado");
        SecurityContextHolder.getContext().setAuthentication(token);

        meService.findCompanies();

        List<GroupIdMappingTask> tasks = adminTasksService.listUnresolvedGroupIdMappingTasks();
        assertThat(tasks.size(), equalTo(1));
        assertThat(tasks.get(0).groupId(), equalTo(token.getToken().getClaimAsStringList("groups").get(0)));
    }
}
