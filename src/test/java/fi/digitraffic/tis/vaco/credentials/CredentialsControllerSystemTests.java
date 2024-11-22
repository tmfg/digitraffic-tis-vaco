package fi.digitraffic.tis.vaco.credentials;

import com.fasterxml.jackson.core.type.TypeReference;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.api.model.credentials.CreateCredentialsRequest;
import fi.digitraffic.tis.vaco.api.model.credentials.ImmutableCreateCredentialsRequest;
import fi.digitraffic.tis.vaco.api.model.credentials.ImmutableUpdateCredentialsRequest;
import fi.digitraffic.tis.vaco.api.model.credentials.UpdateCredentialsRequest;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.credentials.model.AuthenticationDetails;
import fi.digitraffic.tis.vaco.credentials.model.Credentials;
import fi.digitraffic.tis.vaco.credentials.model.CredentialsType;
import fi.digitraffic.tis.vaco.credentials.model.ImmutableHttpBasicAuthenticationDetails;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;

import java.util.List;

import static fi.digitraffic.tis.vaco.hamcrest.VacoMatchers.nanoId;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class CredentialsControllerSystemTests extends SpringBootIntegrationTestBase  {

    private final TypeReference<Resource<Credentials>> credentialsResourceType = new TypeReference<>() {};

    private final TypeReference<Resource<List<Credentials>>> listCredentialsResourceType = new TypeReference<>() {};

    private final TypeReference<Resource<Boolean>> deleteResourceType = new TypeReference<>() {};

    @BeforeAll
    static void createRequiredQueues() {
        createQueue("vaco-errors");
        createQueue("rules-results");
        createQueue("rules-processing-gtfs-canonical");
        createQueue("DLQ-rules-processing");
    }

    @BeforeAll
    static void createRequiredBuckets(@Autowired VacoProperties vacoProperties) {
        CreateBucketResponse r = createBucket(vacoProperties.s3ProcessingBucket());
    }

    @Test
    void canPerformAllStandardCRUDOperations() throws Exception {
        String name = "test credentials";
        String description = "this is only for testing";
        String owner = Constants.FINTRAFFIC_BUSINESS_ID;

        Resource<Credentials> createdCredentials = createCredentials(CredentialsType.HTTP_BASIC, name, description, owner, ImmutableHttpBasicAuthenticationDetails.of("userId", "password"));

        try {
            // 1. create new credentials
            assertAll("Created subscription matches with request",
                () -> assertThat(createdCredentials.data().publicId(), nanoId()),
                () -> assertThat(createdCredentials.data().owner().businessId(), equalTo(owner)),
                () -> assertThat(createdCredentials.data().name(), equalTo(name)),
                () -> assertThat(createdCredentials.data().description(), equalTo(description)),
                () -> assertThat("AuthenticationDetails should not be exposed in response", createdCredentials.data().details(), Matchers.nullValue()));

            // 2. list all, verify listed is the same as previously created
            Resource<List<Credentials>> allCredentialsForBusinessId = listCredentials(owner);

            assertThat(allCredentialsForBusinessId.data().size(), equalTo(1));
            assertThat(allCredentialsForBusinessId.data().get(0), equalTo(createdCredentials.data()));

            // 3. can fetch specific credentials with publicId
            assertThat(fetchCredentials(createdCredentials.data().publicId()).data(), equalTo(createdCredentials.data()));

            // 4.update credentials, all allowed fields (technically speaking type can also be changed, but we have only single type)
            Resource<Credentials> updatedCredentials = updateCredentials(
                createdCredentials.data().publicId(),
                "updated " + name,
                "updated " + description,
                ImmutableHttpBasicAuthenticationDetails.of("another", "secret"));
            assertAll("Updated subscription matches with request",
                () -> assertThat("publicId cannot be changed", updatedCredentials.data().publicId(), equalTo(createdCredentials.data().publicId())),
                () -> assertThat("owner cannot be changed", updatedCredentials.data().owner(), equalTo(createdCredentials.data().owner())),
                () -> assertThat("name was prefixed with 'updated'", updatedCredentials.data().name(), equalTo("updated " + createdCredentials.data().name())),
                () -> assertThat("description was prefixed with 'updated'", updatedCredentials.data().description(), equalTo("updated " + createdCredentials.data().description())),
                () -> assertThat("AuthenticationDetails should not be exposed in response", updatedCredentials.data().details(), Matchers.nullValue()));
        } finally {
            // 5. we assume delete always works :-)
            assertThat(deleteCredentials(createdCredentials.data().publicId()).data(), equalTo(true));
        }
    }

    private Resource<Credentials> createCredentials(CredentialsType type, String name, String description, String owner, AuthenticationDetails details) throws Exception {
        CreateCredentialsRequest createRequest = ImmutableCreateCredentialsRequest.builder()
            .type(type)
            .name(name)
            .description(description)
            .owner(owner)
            .details(details)
            .build();

        MvcResult createResponse = apiCall(post("/v1/credentials").content(toJson(createRequest)))
            .andExpect(status().isCreated())
            .andReturn();
        return apiResponse(createResponse, credentialsResourceType);
    }

    private Resource<List<Credentials>> listCredentials(String businessId) throws Exception {
        MvcResult createResponse = apiCall(get("/v1/credentials").param("businessId", businessId))
            .andExpect(status().isOk())
            .andReturn();
        return apiResponse(createResponse, listCredentialsResourceType);
    }

    private Resource<Credentials> fetchCredentials(String publicId) throws Exception {
        MvcResult createResponse = apiCall(get("/v1/credentials/" + publicId))
            .andExpect(status().isOk())
            .andReturn();
        return apiResponse(createResponse, credentialsResourceType);
    }

    private Resource<Credentials> updateCredentials(String publicId, String name, String description, AuthenticationDetails details) throws Exception {
        UpdateCredentialsRequest updateRequest = ImmutableUpdateCredentialsRequest.builder()
            .name(name)
            .description(description)
            .details(details)
            .build();

        MvcResult updateResponse = apiCall(put("/v1/credentials/" + publicId).content(toJson(updateRequest)))
            .andExpect(status().isOk())
            .andReturn();
        return apiResponse(updateResponse, credentialsResourceType);
    }

    private Resource<Boolean> deleteCredentials(String publicId) throws Exception {
        MvcResult updateResponse = apiCall(delete("/v1/credentials/" + publicId))
            .andExpect(status().isOk())
            .andReturn();
        return apiResponse(updateResponse, deleteResourceType);
    }
}
