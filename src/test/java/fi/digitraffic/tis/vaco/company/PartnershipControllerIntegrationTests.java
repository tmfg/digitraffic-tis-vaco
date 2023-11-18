package fi.digitraffic.tis.vaco.company;

import com.fasterxml.jackson.core.type.TypeReference;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.company.dto.ImmutablePartnershipRequest;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
import fi.digitraffic.tis.vaco.company.model.ImmutablePartnership;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PartnershipControllerIntegrationTests extends SpringBootIntegrationTestBase {
    TypeReference<Resource<ImmutablePartnership>> partnershipRequestType = new TypeReference<>() {};
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
    void canCreatePartnership() throws Exception {
        ImmutablePartnershipRequest partnershipRequest = TestObjects.aPartnershipRequest()
            .partnerABusinessId(companyA.businessId())
            .partnerBBusinessId(companyB.businessId())
            .build();
        MvcResult response = apiCall(post("/partnership").content(toJson(partnershipRequest)))
            .andExpect(status().isOk())
            .andReturn();

        ImmutablePartnership createdPartnershipRequest = apiResponse(response, partnershipRequestType).data();

        assertAll("Base fields are stored properly",
            () -> assertThat(createdPartnershipRequest.type(), equalTo(partnershipRequest.type())),
            () -> assertThat(createdPartnershipRequest.partnerA().businessId(), equalTo(partnershipRequest.partnerABusinessId())),
            () -> assertThat(createdPartnershipRequest.partnerB().businessId(), equalTo(partnershipRequest.partnerBBusinessId())));
    }

    @Test
    void duplicatePartnershipCreationFails() throws Exception {
        ImmutablePartnershipRequest partnershipRequest = TestObjects.aPartnershipRequest()
            .partnerABusinessId(companyA.businessId())
            .partnerBBusinessId(companyB.businessId())
            .build();
        apiCall(post("/partnership").content(toJson(partnershipRequest)))
            .andExpect(status().isOk())
            .andReturn();
        apiCall(post("/partnership").content(toJson(partnershipRequest)))
            .andExpect(status().isConflict())
            .andReturn();
    }

    @Test
    void partnershipCreationBetweenSamePartnersFails() throws Exception {
        ImmutablePartnershipRequest partnershipRequest = TestObjects.aPartnershipRequest()
            .partnerABusinessId(companyA.businessId())
            .partnerBBusinessId(companyA.businessId())
            .build();
        apiCall(post("/partnership").content(toJson(partnershipRequest)))
            .andExpect(status().isBadRequest())
            .andReturn();
    }

    @Test
    void partnershipCreationBetweenNonExistingPartnersFails() throws Exception {
        ImmutablePartnershipRequest partnershipRequest = TestObjects.aPartnershipRequest()
            .partnerABusinessId(UUID.randomUUID().toString())
            .partnerBBusinessId(companyB.businessId())
            .build();
        apiCall(post("/partnership").content(toJson(partnershipRequest)))
            .andExpect(status().isBadRequest())
            .andReturn();

        ImmutablePartnershipRequest partnershipRequest2 = TestObjects.aPartnershipRequest()
            .partnerABusinessId(companyA.businessId())
            .partnerBBusinessId(UUID.randomUUID().toString())
            .build();
        apiCall(post("/partnership").content(toJson(partnershipRequest2)))
            .andExpect(status().isBadRequest())
            .andReturn();
    }
}
