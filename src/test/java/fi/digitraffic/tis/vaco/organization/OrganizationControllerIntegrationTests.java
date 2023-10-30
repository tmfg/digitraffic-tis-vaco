package fi.digitraffic.tis.vaco.organization;

import com.fasterxml.jackson.core.type.TypeReference;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.organization.model.ImmutableOrganization;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrganizationControllerIntegrationTests extends SpringBootIntegrationTestBase {

    TypeReference<Resource<ImmutableOrganization>> organizationResourceType = new TypeReference<>() {};

    @Test
    void canCreateOrganization() throws Exception {
        ImmutableOrganization organization = TestObjects.anOrganization().build();
        MvcResult response = apiCall(post("/organization").content(toJson(organization)))
            .andExpect(status().isOk())
            .andReturn();
        Resource<ImmutableOrganization> createdOrganization = apiResponse(response, organizationResourceType);

        assertAll("Base fields are stored properly",
            () -> assertThat(createdOrganization.data().businessId(), equalTo(organization.businessId())),
            () -> assertThat(createdOrganization.data().name(), equalTo(organization.name())));
        assertThat("API endpoints should not expose internal IDs.", createdOrganization.data().id(), is(nullValue()));

        // follow the self-reference link from previous response
        MvcResult fetchResponse = apiCall(createdOrganization.links().get("refs").get("self"))
            .andExpect(status().isOk())
            .andReturn();
        var fetchResult = apiResponse(fetchResponse, organizationResourceType);

        // assert provided data has stayed the same
        assertAll("Base fields are stored properly",
            () -> assertThat(fetchResult.data().businessId(), equalTo(organization.businessId())),
            () -> assertThat(fetchResult.data().name(), equalTo(organization.name())));

        assertThat("API endpoints should not expose internal IDs.", fetchResult.data().id(), is(nullValue()));
    }

    @Test
    void duplicateOrganizationCreationFails() throws Exception {
        ImmutableOrganization organization = TestObjects.anOrganization().build();
        apiCall(post("/organization").content(toJson(organization)));
        apiCall(post("/organization").content(toJson(organization)))
            .andExpect(status().isConflict())
            .andReturn();
    }

    @Test
    void canFetchOrganizationByBusinessId() throws Exception {
        ImmutableOrganization organization = TestObjects.anOrganization().build();
        apiCall(post("/organization").content(toJson(organization)))
            .andExpect(status().isOk());

        MvcResult response = apiCall(get("/organization/{businessId}", organization.businessId()))
            .andExpect(status().isOk())
            .andReturn();

        Resource<ImmutableOrganization> fetchedOrganization = apiResponse(response, organizationResourceType);
        assertAll("Base fields are fetched properly",
            () -> assertThat(fetchedOrganization.data().businessId(), equalTo(organization.businessId())),
            () -> assertThat(fetchedOrganization.data().name(), equalTo(organization.name())));
        assertThat("API endpoints should not expose internal IDs.", fetchedOrganization.data().id(), is(nullValue()));
    }

    @Test
    void fetchingNonExistingOrganizationFails() throws Exception {
        apiCall(get("/organization/{businessId}", UUID.randomUUID().toString()))
            .andExpect(status().isNotFound())
            .andReturn();
    }
}
