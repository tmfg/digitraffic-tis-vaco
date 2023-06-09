package fi.digitraffic.tis.vaco.organization;

import com.fasterxml.jackson.core.type.TypeReference;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.organization.dto.ImmutableCooperationCommand;
import fi.digitraffic.tis.vaco.organization.model.ImmutableOrganization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class CooperationControllerIntegrationTests extends SpringBootIntegrationTestBase {
    TypeReference<ImmutableCooperationCommand> cooperationCommandType = new TypeReference<>() {};
    ImmutableOrganization organizationA = TestObjects.anOrganization().build();
    ImmutableOrganization organizationB = TestObjects.anOrganization().build();

    @BeforeEach
    void init() throws Exception {
        apiCall(post("/organization").content(toJson(organizationA)))
            .andExpect(status().isOk())
            .andReturn();
        apiCall(post("/organization").content(toJson(organizationB)))
            .andExpect(status().isOk())
            .andReturn();
    }

    @Test
    void canCreateCooperation() throws Exception {
        ImmutableCooperationCommand cooperationCommand = TestObjects.aCooperationCommand()
            .partnerABusinessId(organizationA.businessId())
            .partnerBBusinessId(organizationB.businessId())
            .build();
        MvcResult response = apiCall(post("/cooperation").content(toJson(cooperationCommand)))
            .andExpect(status().isOk())
            .andReturn();

        ImmutableCooperationCommand createdcooperationCommand = apiResponse(response, cooperationCommandType);

        assertAll("Base fields are stored properly",
            () -> assertThat(createdcooperationCommand.cooperationType(), equalTo(cooperationCommand.cooperationType())),
            () -> assertThat(createdcooperationCommand.partnerABusinessId(), equalTo(cooperationCommand.partnerABusinessId())),
            () -> assertThat(createdcooperationCommand.partnerBBusinessId(), equalTo(cooperationCommand.partnerBBusinessId())));
    }

    @Test
    void duplicateCooperationCreationFails() throws Exception {
        ImmutableCooperationCommand cooperationCommand = TestObjects.aCooperationCommand()
            .partnerABusinessId(organizationA.businessId())
            .partnerBBusinessId(organizationB.businessId())
            .build();
        apiCall(post("/cooperation").content(toJson(cooperationCommand)))
            .andExpect(status().isOk())
            .andReturn();
        apiCall(post("/cooperation").content(toJson(cooperationCommand)))
            .andExpect(status().isConflict())
            .andReturn();
    }

    @Test
    void cooperationCreationBetweenSamePartnersFails() throws Exception {
        ImmutableCooperationCommand cooperationCommand = TestObjects.aCooperationCommand()
            .partnerABusinessId(organizationA.businessId())
            .partnerBBusinessId(organizationA.businessId())
            .build();
        apiCall(post("/cooperation").content(toJson(cooperationCommand)))
            .andExpect(status().isBadRequest())
            .andReturn();
    }

    @Test
    void cooperationCreationBetweenNonExistingPartnersFails() throws Exception {
        ImmutableCooperationCommand cooperationCommand = TestObjects.aCooperationCommand()
            .partnerABusinessId(UUID.randomUUID().toString())
            .partnerBBusinessId(organizationB.businessId())
            .build();
        apiCall(post("/cooperation").content(toJson(cooperationCommand)))
            .andExpect(status().isBadRequest())
            .andReturn();

        ImmutableCooperationCommand cooperationCommand2 = TestObjects.aCooperationCommand()
            .partnerABusinessId(organizationA.businessId())
            .partnerBBusinessId(UUID.randomUUID().toString())
            .build();
        apiCall(post("/cooperation").content(toJson(cooperationCommand)))
            .andExpect(status().isBadRequest())
            .andReturn();
    }
}
