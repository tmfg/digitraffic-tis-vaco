package fi.digitraffic.tis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.utilities.dto.Link;
import fi.digitraffic.tis.vaco.VacoApplication;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.UnsupportedEncodingException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base class to extend from when implementing Spring Boot integration tests.
 *
 *  - Wires Flyway and Testcontainers to Spring Data repositories with all necessary DI
 *  - Runs migrations from the `digitraffic-tis-dbmigrator` project
 *  - Provides useful helpers for API endpoint testing
 */
@Testcontainers
@SpringBootTest(
    classes = VacoApplication.class,
    webEnvironment = WebEnvironment.RANDOM_PORT
)
@AutoConfigureMockMvc(addFilters = false)
@DirtiesContext
@ContextConfiguration(classes = OverridesConfiguration.class)
public abstract class SpringBootIntegrationTestBase extends AwsIntegrationTestBase {

    @Container
    public static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer<>("postgres:15-bullseye")
        .withDatabaseName("vaco")
        .withUsername("postgres")
        .withPassword("h.pG2S\\~*H<-agvw")
        .withUrlParam("currentSchema", "vaco");

    // TODO: This works, but is rather verbose and ugly so we might want to replace this with test profile
    @DynamicPropertySource
    public static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.flyway.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.flyway.user", postgreSQLContainer::getUsername);
        registry.add("spring.flyway.password", postgreSQLContainer::getPassword);
        registry.add("spring.flyway.createSchemas", () -> true);
        registry.add("spring.flyway.schemas", () -> "vaco");
        registry.add("spring.flyway.locations", () -> "filesystem:../digitraffic-tis-dbmigrator/db/migrations");
        registry.add("spring.flyway.fail-on-missing-locations", () -> true);
        registry.add("spring.cloud.aws.sqs.endpoint", () -> localstack.getEndpointOverride(Service.SQS));
        registry.add("spring.cloud.aws.s3.endpoint", () -> localstack.getEndpointOverride(Service.S3));
        registry.add("vaco.aws.region", () -> localstack.getRegion());
        registry.add("vaco.aws.endpoint", () -> localstack.getEndpoint());
        registry.add("vaco.scheduling.enable", () -> false);
        registry.add("spring.cloud.azure.active-directory.enabled", () -> false);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    protected ResultActions apiCall(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON));
    }

    @NotNull
    protected static Link toLink(JsonNode jsonNode) {
        return new Link(
                jsonNode.get("href").textValue(),
                RequestMethod.valueOf(jsonNode.get("method").textValue()));
    }

    protected ResultActions apiCall(Link link) throws Exception {
        return apiCall(switch (link.method()) {
            case GET -> get(link.href());
            case POST -> post(link.href());
            default -> throw new AssertionError("%s %s not supported! Please define functionality to apiCall() method for this".formatted(link.method(), link.href()));
        });
    }

    protected <C> String toJson(C command) throws JsonProcessingException {
        return objectMapper.writeValueAsString(command);
    }

    protected <T> T apiResponse(MvcResult response, TypeReference<T> result) throws UnsupportedEncodingException, JsonProcessingException {
        return objectMapper.readValue(response.getResponse().getContentAsString(), result);
    }

    /**
     * Raw JSON variant of {@link #apiResponse(MvcResult, TypeReference)}. Use when internal ids missing from response
     * due to JSON visibility limits prevent deserialization to our model objects.
     *
     * @param response Spring Mvc rsponse
     * @return Response body as JsonNode
     * @throws UnsupportedEncodingException Thrown if Spring somehow forgets Java Strings are UTF-8/UCS-16
     * @throws JsonProcessingException Thrown if response cannot be mapped to JSON
     */
    protected JsonNode apiResponse(MvcResult response) throws UnsupportedEncodingException, JsonProcessingException {
        return objectMapper.readTree(response.getResponse().getContentAsString());
    }

    @Autowired
    protected CompanyHierarchyService companyHierarchyService;

    /**
     * Make current context believe given token is Fintraffic company.
     * <p>
     * This is about 3/5 on scale of "how hacky this is".
     */
    protected void injectGroupIdToCompany(JwtAuthenticationToken token) {
        companyHierarchyService.updateAdGroupId(
            companyHierarchyService.findByBusinessId(Constants.FINTRAFFIC_BUSINESS_ID).get(),
            token.getToken().getClaimAsStringList("groups").get(0));
    }

}
