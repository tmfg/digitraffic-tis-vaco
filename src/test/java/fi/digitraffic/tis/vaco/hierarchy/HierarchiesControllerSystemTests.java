package fi.digitraffic.tis.vaco.hierarchy;

import com.fasterxml.jackson.core.type.TypeReference;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.api.model.hierarchies.CreateHierarchyRequest;
import fi.digitraffic.tis.vaco.api.model.hierarchies.ImmutableCreateHierarchyRequest;
import fi.digitraffic.tis.vaco.api.model.refs.ImmutableCompanyRef;
import fi.digitraffic.tis.vaco.company.model.HierarchyType;
import fi.digitraffic.tis.vaco.hierarchy.model.Hierarchy;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HierarchiesControllerSystemTests extends SpringBootIntegrationTestBase {

    TypeReference<Resource<Hierarchy>> hierarchyResourceType = new TypeReference<>() {};

    @Test
    void canCreateNewHierarchy() throws Exception {
        CreateHierarchyRequest createRequest = ImmutableCreateHierarchyRequest.of(
            HierarchyType.AUTHORITY_PROVIDER,
            ImmutableCompanyRef.of(Constants.FINTRAFFIC_BUSINESS_ID));

        MvcResult createResponse = apiCall(post("/v1/hierarchies").content(toJson(createRequest)))
            .andExpect(status().isCreated())
            .andReturn();
        Resource<Hierarchy> createdHierarchy = apiResponse(createResponse, hierarchyResourceType);

        assertAll("Created hierarchy matches with request",
            () -> assertThat(createdHierarchy.data().type(), equalTo(createRequest.type())),
            () -> assertThat(createdHierarchy.data().root().businessId(), equalTo(createRequest.company().businessId())));
    }
}
