package fi.digitraffic.tis.vaco.company;

import com.fasterxml.jackson.core.type.TypeReference;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
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

class CompanyControllerIntegrationTests extends SpringBootIntegrationTestBase {

    TypeReference<Resource<ImmutableCompany>> companyResourceType = new TypeReference<>() {};

    @Test
    void canCreateCompany() throws Exception {
        ImmutableCompany company = TestObjects.aCompany().build();
        MvcResult response = apiCall(post("/company").content(toJson(company)))
            .andExpect(status().isOk())
            .andReturn();
        Resource<ImmutableCompany> createdCompany = apiResponse(response, companyResourceType);

        assertAll("Base fields are stored properly",
            () -> assertThat(createdCompany.data().businessId(), equalTo(company.businessId())),
            () -> assertThat(createdCompany.data().name(), equalTo(company.name())));
        assertThat("API endpoints should not expose internal IDs.", createdCompany.data().id(), is(nullValue()));

        // follow the self-reference link from previous response
        MvcResult fetchResponse = apiCall(createdCompany.links().get("refs").get("self"))
            .andExpect(status().isOk())
            .andReturn();
        var fetchResult = apiResponse(fetchResponse, companyResourceType);

        // assert provided data has stayed the same
        assertAll("Base fields are stored properly",
            () -> assertThat(fetchResult.data().businessId(), equalTo(company.businessId())),
            () -> assertThat(fetchResult.data().name(), equalTo(company.name())));

        assertThat("API endpoints should not expose internal IDs.", fetchResult.data().id(), is(nullValue()));
    }

    @Test
    void duplicateCompanyCreationFails() throws Exception {
        ImmutableCompany company = TestObjects.aCompany().build();
        apiCall(post("/company").content(toJson(company)));
        apiCall(post("/company").content(toJson(company)))
            .andExpect(status().isConflict())
            .andReturn();
    }

    @Test
    void canFetchCompanyByBusinessId() throws Exception {
        ImmutableCompany company = TestObjects.aCompany().build();
        apiCall(post("/company").content(toJson(company)))
            .andExpect(status().isOk());

        MvcResult response = apiCall(get("/company/{businessId}", company.businessId()))
            .andExpect(status().isOk())
            .andReturn();

        Resource<ImmutableCompany> fetchedCompany = apiResponse(response, companyResourceType);
        assertAll("Base fields are fetched properly",
            () -> assertThat(fetchedCompany.data().businessId(), equalTo(company.businessId())),
            () -> assertThat(fetchedCompany.data().name(), equalTo(company.name())));
        assertThat("API endpoints should not expose internal IDs.", fetchedCompany.data().id(), is(nullValue()));
    }

    @Test
    void fetchingNonExistingCompanyFails() throws Exception {
        apiCall(get("/company/{businessId}", UUID.randomUUID().toString()))
            .andExpect(status().isNotFound())
            .andReturn();
    }
}
