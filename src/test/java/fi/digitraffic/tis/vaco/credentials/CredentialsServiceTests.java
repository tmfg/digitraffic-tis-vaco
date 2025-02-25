package fi.digitraffic.tis.vaco.credentials;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.http.HttpClient;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import fi.digitraffic.tis.vaco.credentials.model.CredentialsType;
import fi.digitraffic.tis.vaco.credentials.model.HttpBasicAuthenticationDetails;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableCompanyRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableCredentialsRecord;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.http.VacoHttpClient;
import fi.digitraffic.tis.vaco.queuehandler.mapper.EntryRequestMapper;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CredentialsServiceTests {
    @Mock
    private VacoHttpClient vacoHttpClient;
    @Mock
    private EntryRequestMapper entryRequestMapper;
    @Mock
    private CompanyHierarchyService companyHierarchyService;
    @Mock
    private CredentialsRepository credentialsRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private HttpClient httpClient;
    @Mock
    private EntryService entryService;
    private Map<String, String> requestHeaders = new HashMap<>();
    private ObjectMapper objectMapper;
    private CompanyRecord companyRecord;
    private ImmutableCredentialsRecord credentialsRecord;
    private String businessId;
    private Entry entry;
    private byte [] detailsBytes;

    @BeforeEach
    void setUp() {

        objectMapper = new ObjectMapper();
        RecordMapper recordMapper = new RecordMapper(objectMapper);

        CredentialsService credentialsService = new CredentialsService(
                entryRequestMapper, companyHierarchyService,
                credentialsRepository, companyRepository, recordMapper);

        vacoHttpClient = new VacoHttpClient(httpClient, credentialsService, entryService);

        businessId = Constants.FINTRAFFIC_BUSINESS_ID;

        companyRecord = ImmutableCompanyRecord.of(3000000L, businessId, true);

        entry = ImmutableEntry.copyOf(TestObjects.anEntry("gtfs").build());

        String authDetailsJson = "{\"userId\":\"testUser\", \"password\":\"testPassword\"}";
        detailsBytes = authDetailsJson.getBytes(StandardCharsets.UTF_8);

    }

    @Test
    void testCredentialsAutomaticallySet() throws JsonProcessingException {

        credentialsRecord = ImmutableCredentialsRecord.builder()
            .id(120000)
            .publicId(NanoIdUtils.randomNanoId())
            .type(CredentialsType.HTTP_BASIC)
            .name("name")
            .description("test")
            .ownerId(3000000L)
            .details(detailsBytes)
            .urlPattern("(?<scheme>https?)://(?<domain>[^/]+)(?<path>/[^?]+(?<!/.zip))?$")
            .build();

        when(companyRepository.findByBusinessId(businessId)).thenReturn(Optional.of(companyRecord));

        when(credentialsRepository.findAllForCompany(companyRecord)).thenReturn(List.of(credentialsRecord));

        requestHeaders = vacoHttpClient.addAuthorizationHeaderAutomatically(businessId, "https://test.fintraffic.fi/exports/test.zip", entry);

        String detailsJson = new String(credentialsRecord.details());
        HttpBasicAuthenticationDetails details = objectMapper.readValue(detailsJson, HttpBasicAuthenticationDetails.class);
        String userId = details.userId();
        String password = details.password();

        assertTrue(requestHeaders.containsKey("Authorization"));
        String authHeader = requestHeaders.get("Authorization");
        String expectedAuthValue = "Basic " + Base64.getEncoder().encodeToString((userId + ":" + password).getBytes());
        assertEquals(expectedAuthValue, authHeader);

    }

    @Test
    void testCredentialsNotAutomaticallySet() {

        String detailsJson = "{\"userId\":\"testUser\", \"password\":\"testPassword\"}";
        byte[] detailsBytes = detailsJson.getBytes(StandardCharsets.UTF_8);

        credentialsRecord = ImmutableCredentialsRecord.builder()
            .id(120000)
            .publicId(NanoIdUtils.randomNanoId())
            .type(CredentialsType.HTTP_BASIC)
            .name("name")
            .description("test")
            .ownerId(3000000L)
            .details(detailsBytes)
            .urlPattern("(?<scheme>https?)://(?<domain>[^/]+)(?<path>/[^?]+(?<!\\.zip))?$")
            .build();

        when(companyRepository.findByBusinessId(businessId)).thenReturn(Optional.of(companyRecord));

        when(credentialsRepository.findAllForCompany(companyRecord)).thenReturn(List.of(credentialsRecord));

        requestHeaders = vacoHttpClient.addAuthorizationHeaderAutomatically(businessId, "https://test.fi/exports/test.zip", entry);

        assertTrue(requestHeaders.isEmpty());

    }
}
