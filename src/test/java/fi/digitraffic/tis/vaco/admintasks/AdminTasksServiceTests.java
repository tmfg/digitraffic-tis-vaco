package fi.digitraffic.tis.vaco.admintasks;

import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.admintasks.model.GroupIdMappingTask;
import fi.digitraffic.tis.vaco.admintasks.model.ImmutableGroupIdMappingTask;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.service.CompanyService;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@ExtendWith(MockitoExtension.class)
class AdminTasksServiceTests extends SpringBootIntegrationTestBase {

    @Autowired
    private AdminTasksService adminTasksService;

    @Autowired
    private CompanyService companyService;

    private GroupIdMappingTask task;
    private Company company;

    private List<GroupIdMappingTask> tasksToCleanup;

    @BeforeEach
    void setUp() {
        tasksToCleanup = new ArrayList<>();
        task = adminTasksService.registerGroupIdMappingTask(TestObjects.adminGroupId().build());
        tasksToCleanup.add(task);
        company = companyService.findByBusinessId(Constants.FINTRAFFIC_BUSINESS_ID).orElseThrow();
    }

    @AfterEach
    void tearDown() {
        // cleanup is useful for both UI to allow re-registering group id:s and to keep these tests clean
        tasksToCleanup.forEach(task -> assertThat(adminTasksService.removeGroupIdMappingTask(task), equalTo(true)));
    }

    @Test
    void canCreateAndResolveGroupIdMappingTask() {
        Pair<GroupIdMappingTask, Company> resolved = adminTasksService.resolveAsPairedToCompany(task, company);

        assertThat(resolved.getLeft().completed(), notNullValue());
        assertThat(resolved.getRight().adGroupId(), equalTo(task.groupId()));
    }

    @Test
    void canMarkGroupIdAsCompleteAndIgnored() {
        GroupIdMappingTask resolved = adminTasksService.resolveAsSkippable(task);

        assertThat(resolved.skip(), equalTo(true));
    }

    @Test
    void canResolveWithBothInternalIdAndPublicId() {
        GroupIdMappingTask taskWithId = adminTasksService.registerGroupIdMappingTask(TestObjects.adminGroupId().build());
        tasksToCleanup.add(taskWithId);
        assertThat(adminTasksService.resolveAsSkippable(taskWithId).skip(), equalTo(true));

        GroupIdMappingTask taskWithPublicId = adminTasksService.registerGroupIdMappingTask(TestObjects.adminGroupId().build());
        tasksToCleanup.add(taskWithPublicId);

        assertThat(adminTasksService.resolveAsSkippable(ImmutableGroupIdMappingTask.copyOf(taskWithPublicId).withId(null)).skip(), equalTo(true));
    }

    @Test
    void canListUnresolvedGroupIdMappingTasks() {
        // reuse common object to avoid cleanup getting confused
        GroupIdMappingTask a = task;
        GroupIdMappingTask b = adminTasksService.registerGroupIdMappingTask(TestObjects.adminGroupId().build());
        GroupIdMappingTask c = adminTasksService.registerGroupIdMappingTask(TestObjects.adminGroupId().build());
        tasksToCleanup.add(b);
        tasksToCleanup.add(c);

        adminTasksService.resolveAsPairedToCompany(a, company);
        adminTasksService.resolveAsSkippable(b);

        List<GroupIdMappingTask> open = adminTasksService.listUnresolvedGroupIdMappingTasks();

        assertThat(open, equalTo(List.of(c)));
    }

}
