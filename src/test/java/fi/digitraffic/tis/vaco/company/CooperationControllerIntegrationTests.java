package fi.digitraffic.tis.vaco.company;

import com.fasterxml.jackson.core.type.TypeReference;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.company.dto.ImmutableCooperationRequest;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
import fi.digitraffic.tis.vaco.company.model.ImmutableCooperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CooperationControllerIntegrationTests extends SpringBootIntegrationTestBase {
    TypeReference<Resource<ImmutableCooperation>> cooperationRequestType = new TypeReference<>() {};
    ImmutableCompany companyA = TestObjects.aCompany().build();
    ImmutableCompany companyB = TestObjects.aCompany().build();

    @BeforeEach
    void init() throws Exception {
        apiCall(post("/company").content(toJson(companyA)))
            .andExpect(status().isOk())
            .andReturn();
        apiCall(post("/company").content(toJson(companyB)))
            .andExpect(status().isOk())
            .andReturn();
    }

    @Test
    void canCreateCooperation() throws Exception {
        ImmutableCooperationRequest cooperationRequest = TestObjects.aCooperationRequest()
            .partnerABusinessId(companyA.businessId())
            .partnerBBusinessId(companyB.businessId())
            .build();
        MvcResult response = apiCall(post("/cooperation").content(toJson(cooperationRequest)))
            .andExpect(status().isOk())
            .andReturn();

        ImmutableCooperation createdCooperationRequest = apiResponse(response, cooperationRequestType).data();

        assertAll("Base fields are stored properly",
            () -> assertThat(createdCooperationRequest.cooperationType(), equalTo(cooperationRequest.cooperationType())),
            () -> assertThat(createdCooperationRequest.partnerA().businessId(), equalTo(cooperationRequest.partnerABusinessId())),
            () -> assertThat(createdCooperationRequest.partnerB().businessId(), equalTo(cooperationRequest.partnerBBusinessId())));
    }

    @Test
    void duplicateCooperationCreationFails() throws Exception {
        ImmutableCooperationRequest cooperationRequest = TestObjects.aCooperationRequest()
            .partnerABusinessId(companyA.businessId())
            .partnerBBusinessId(companyB.businessId())
            .build();
        apiCall(post("/cooperation").content(toJson(cooperationRequest)))
            .andExpect(status().isOk())
            .andReturn();
        apiCall(post("/cooperation").content(toJson(cooperationRequest)))
            .andExpect(status().isConflict())
            .andReturn();
    }

    @Test
    void cooperationCreationBetweenSamePartnersFails() throws Exception {
        ImmutableCooperationRequest cooperationRequest = TestObjects.aCooperationRequest()
            .partnerABusinessId(companyA.businessId())
            .partnerBBusinessId(companyA.businessId())
            .build();
        apiCall(post("/cooperation").content(toJson(cooperationRequest)))
            .andExpect(status().isBadRequest())
            .andReturn();
    }

    @Test
    void cooperationCreationBetweenNonExistingPartnersFails() throws Exception {
        ImmutableCooperationRequest cooperationRequest = TestObjects.aCooperationRequest()
            .partnerABusinessId(UUID.randomUUID().toString())
            .partnerBBusinessId(companyB.businessId())
            .build();
        apiCall(post("/cooperation").content(toJson(cooperationRequest)))
            .andExpect(status().isBadRequest())
            .andReturn();

        ImmutableCooperationRequest cooperationRequest2 = TestObjects.aCooperationRequest()
            .partnerABusinessId(companyA.businessId())
            .partnerBBusinessId(UUID.randomUUID().toString())
            .build();
        apiCall(post("/cooperation").content(toJson(cooperationRequest2)))
            .andExpect(status().isBadRequest())
            .andReturn();
    }
}
