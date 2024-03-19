package fi.digitraffic.tis.vaco.admintasks;

import com.fasterxml.jackson.core.type.TypeReference;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.admintasks.model.GroupIdMappingTask;
import fi.digitraffic.tis.vaco.admintasks.model.ImmutableGroupIdMappingTask;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminTasksControllerIntegrationTests extends SpringBootIntegrationTestBase {
    TypeReference<Resource<List<GroupIdMappingTask>>> listGroupIdMappingTaskType = new TypeReference<>() {};
    TypeReference<Resource<GroupIdMappingTask>> groupIdMappingTaskType = new TypeReference<>() {};

    @Autowired
    private AdminTasksService adminTasksService;
    @Autowired
    private CompanyHierarchyService companyHierarchyService;

    @Test
    void canListAllUnresolvedGroupIdMappingTasks() throws Exception {
        GroupIdMappingTask aTask = adminTasksService.registerGroupIdMappingTask(ImmutableGroupIdMappingTask.of(UUID.randomUUID().toString()));
        GroupIdMappingTask bTask = adminTasksService.registerGroupIdMappingTask(ImmutableGroupIdMappingTask.of(UUID.randomUUID().toString()));
        GroupIdMappingTask cTask = adminTasksService.registerGroupIdMappingTask(ImmutableGroupIdMappingTask.of(UUID.randomUUID().toString()));

        MvcResult response = apiCall(get("/admin-tasks/group-ids"))
            .andExpect(status().isOk())
            .andReturn();
        List<GroupIdMappingTask> allTasks = apiResponse(response, listGroupIdMappingTaskType).data();

        // database ids are removed since API responses don't contain those
        assertThat(allTasks,
            equalTo(Streams.collect(List.of(aTask, bTask, cTask), t -> ImmutableGroupIdMappingTask.copyOf(t).withId(null))));
    }

    @Test
    void canMarkGroupIdMappingTaskAsSkipped() throws Exception {
        GroupIdMappingTask newTask = ImmutableGroupIdMappingTask.of(UUID.randomUUID().toString());
        GroupIdMappingTask registeredTask = adminTasksService.registerGroupIdMappingTask(newTask);

        MvcResult response = apiCall(post("/admin-tasks/group-ids/" + registeredTask.publicId() + "/skip"))
            .andExpect(status().isOk())
            .andReturn();
        GroupIdMappingTask skippedTask = apiResponse(response, groupIdMappingTaskType).data();

        assertAll("Base fields are stored properly",
            () -> assertThat(skippedTask.groupId(), equalTo(newTask.groupId())),
            () -> assertThat(skippedTask.skip(), equalTo(true)),
            () -> assertThat(skippedTask.completed(), notNullValue()));
    }

    @Test
    void canMarkGroupIdMappingTaskAsPairedToSpecificBusinessId() throws Exception {
        GroupIdMappingTask newTask = ImmutableGroupIdMappingTask.of(UUID.randomUUID().toString());
        GroupIdMappingTask registeredTask = adminTasksService.registerGroupIdMappingTask(newTask);

        MvcResult response = apiCall(
            post("/admin-tasks/group-ids/" + registeredTask.publicId() + "/assign")
                .queryParam("businessId", Constants.FINTRAFFIC_BUSINESS_ID))
            .andExpect(status().isOk())
            .andReturn();
        GroupIdMappingTask assignedTask = apiResponse(response, groupIdMappingTaskType).data();
        Optional<Company> fintrafficCompany = companyHierarchyService.findByBusinessId(Constants.FINTRAFFIC_BUSINESS_ID);

        assertAll("Base fields are stored properly",
            () -> assertThat(assignedTask.groupId(), equalTo(newTask.groupId())),
            () -> assertThat(assignedTask.skip(), equalTo(false)),
            () -> assertThat(assignedTask.completed(), notNullValue()),
            () -> assertThat(fintrafficCompany.isPresent(), equalTo(true)),
            () -> assertThat(fintrafficCompany.get().adGroupId(), equalTo(assignedTask.groupId())));
    }
}
