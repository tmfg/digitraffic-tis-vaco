package fi.digitraffic.tis.vaco.webhooks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TrivoreWebhooksServiceTests {

    private static final String OIDC_DUMMY_URL = "https://oidc.localhost/server";
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private TrivoreWebhooksService trivoreWebhooksService;
    private ObjectMapper objectMapper;

    @Mock
    private SpringWebClientShim webClient;

    @Captor
    private ArgumentCaptor<Optional<JsonNode>> bodyCaptor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        trivoreWebhooksService = new TrivoreWebhooksService(objectMapper, webClient, OIDC_DUMMY_URL, null, null);
    }

    @Test
    void canHandleUserVerifiedEvent() throws JsonProcessingException {
        JsonNode userVerifiedEvent = objectMapper.readTree("""
            {
                "id": "682337f04b041d1dfff95740",
                "type": "USER_EMAIL_VERIFIED",
                "time": "2025-05-14T07:53:07.716Z",
                "webhookId": "68243ace4b041d1dfff95769",
                "webhookCallId": "68244be34b041d1dfff9577d",
                "namespaceCode": "endusers",
                "data": null
            }
            """);
        given(webClient.executeRequest(
            eq(HttpMethod.GET),
            eq("https://oidc.localhost/server/api/rest/v1/user/682337f04b041d1dfff95740"),
            any(Map.class))
        ).willReturn(Optional.ofNullable(objectMapper.readTree("""
            {
                "id": "682337f04b041d1dfff95740",
                "emails": [{"verified":true,"address":"example@email.address"}],
                "memberOf": ["existinggroup"]
            }
            """)));
        given(webClient.executeRequest(
            eq(HttpMethod.GET),
            eq("https://oidc.localhost/server/api/rest/v1/group"),
            any(Map.class))
        ).willReturn(Optional.ofNullable(objectMapper.readTree("""
            {
                "resources": [{
                    "id": "newgroup",
                    "customFields": {
                        "mailDomains": ["email.address"]
                    }
                }]
            }
            """)));
        given(webClient.executeRequest(
            eq(HttpMethod.PUT),
            eq("https://oidc.localhost/server/api/rest/v1/user/682337f04b041d1dfff95740"),
            any(Map.class),
            bodyCaptor.capture())
        ).willAnswer(a -> a.getArgument(3));

        trivoreWebhooksService.handleWebhook(userVerifiedEvent);

        assertThat(
            bodyCaptor.getValue().get(),
            equalTo(objectMapper.readTree("""
                {"id":"682337f04b041d1dfff95740",
                "emails":[{
                    "verified":true,
                    "address":"example@email.address"
                }],
                "memberOf":["existinggroup", "newgroup"]}
            """)));
    }

    @Test
    void patchMergingCanMergeArbitraryJsonNodes() {
        ObjectNode node1 = objectMapper.createObjectNode().put("a", 1).put("b", 2);
        ObjectNode node2 = objectMapper.createObjectNode().put("a", 11).put("d", 4);
        ObjectNode node3 = objectMapper.createObjectNode().put("c", 3);

        JsonNode result = TrivoreWebhooksService.merge(node1, node2, node3);

        assertThat(result, equalTo(objectMapper.createObjectNode().put("a", 11).put("b", 2).put("c", 3).put("d", 4)));
    }

}
