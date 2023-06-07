package fi.digitraffic.tis.vaco.tis;

import com.fasterxml.jackson.core.type.TypeReference;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.tis.model.ImmutableOrganization;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class OrganizationControllerIntegrationTests extends SpringBootIntegrationTestBase {

    TypeReference<ImmutableOrganization> organizationType = new TypeReference<>() {};

    @Test
    void canCreateOrganization() throws Exception {
        ImmutableOrganization organization = TestObjects.anOrganization().build();
        MvcResult response = apiCall(post("/organization").content(toJson(organization)))
            .andExpect(status().isOk())
            .andReturn();
        ImmutableOrganization createdOrganization = apiResponse(response, organizationType);

        assertAll("Base fields are stored properly",
            () -> assertThat(createdOrganization.publicId(), is(notNullValue())),
            () -> assertThat(createdOrganization.businessId(), equalTo(organization.businessId())),
            () -> assertThat(createdOrganization.name(), equalTo(organization.name())));
        assertThat("API endpoints should not expose internal IDs.", createdOrganization.id(), is(nullValue()));
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

        ImmutableOrganization fetchedOrganization = apiResponse(response, organizationType);
        assertAll("Base fields are fetched properly",
            () -> assertThat(fetchedOrganization.publicId(), is(notNullValue())),
            () -> assertThat(fetchedOrganization.businessId(), equalTo(organization.businessId())),
            () -> assertThat(fetchedOrganization.name(), equalTo(organization.name())));
        assertThat("API endpoints should not expose internal IDs.", fetchedOrganization.id(), is(nullValue()));
    }

    @Test
    void fetchingNonExistingOrganizationFails() throws Exception {
        apiCall(get("/organization/{businessId}", UUID.randomUUID().toString()))
            .andExpect(status().isNotFound())
            .andReturn();
    }
}
