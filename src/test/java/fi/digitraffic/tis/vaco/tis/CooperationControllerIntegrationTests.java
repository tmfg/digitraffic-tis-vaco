package fi.digitraffic.tis.vaco.tis;

import com.fasterxml.jackson.core.type.TypeReference;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.tis.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.validation.dto.ImmutableCooperationDto;
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
    TypeReference<ImmutableCooperationDto> cooperationDtoType = new TypeReference<>() {};
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
        ImmutableCooperationDto cooperationDto = TestObjects.aCooperationDto()
            .partnerABusinessId(organizationA.businessId())
            .partnerBBusinessId(organizationB.businessId())
            .build();
        MvcResult response = apiCall(post("/cooperation").content(toJson(cooperationDto)))
            .andExpect(status().isOk())
            .andReturn();

        ImmutableCooperationDto createdCooperationDto = apiResponse(response, cooperationDtoType);

        assertAll("Base fields are stored properly",
            () -> assertThat(createdCooperationDto.cooperationType(), equalTo(cooperationDto.cooperationType())),
            () -> assertThat(createdCooperationDto.partnerABusinessId(), equalTo(cooperationDto.partnerABusinessId())),
            () -> assertThat(createdCooperationDto.partnerBBusinessId(), equalTo(cooperationDto.partnerBBusinessId())));
    }

    @Test
    void duplicateCooperationCreationFails() throws Exception {
        ImmutableCooperationDto cooperationDto = TestObjects.aCooperationDto()
            .partnerABusinessId(organizationA.businessId())
            .partnerBBusinessId(organizationB.businessId())
            .build();
        apiCall(post("/cooperation").content(toJson(cooperationDto)))
            .andExpect(status().isOk())
            .andReturn();
        apiCall(post("/cooperation").content(toJson(cooperationDto)))
            .andExpect(status().isConflict())
            .andReturn();
    }

    @Test
    void cooperationCreationBetweenSamePartnersFails() throws Exception {
        ImmutableCooperationDto cooperationDto = TestObjects.aCooperationDto()
            .partnerABusinessId(organizationA.businessId())
            .partnerBBusinessId(organizationA.businessId())
            .build();
        apiCall(post("/cooperation").content(toJson(cooperationDto)))
            .andExpect(status().isBadRequest())
            .andReturn();
    }

    @Test
    void cooperationCreationBetweenNonExistingPartnersFails() throws Exception {
        ImmutableCooperationDto cooperationDto = TestObjects.aCooperationDto()
            .partnerABusinessId(UUID.randomUUID().toString())
            .partnerBBusinessId(organizationB.businessId())
            .build();
        apiCall(post("/cooperation").content(toJson(cooperationDto)))
            .andExpect(status().isBadRequest())
            .andReturn();

        ImmutableCooperationDto cooperationDto2 = TestObjects.aCooperationDto()
            .partnerABusinessId(organizationA.businessId())
            .partnerBBusinessId(UUID.randomUUID().toString())
            .build();
    }
}
