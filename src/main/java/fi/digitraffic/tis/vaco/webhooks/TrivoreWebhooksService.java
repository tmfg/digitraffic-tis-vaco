package fi.digitraffic.tis.vaco.webhooks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import fi.digitraffic.tis.vaco.VacoException;
import fi.digitraffic.tis.vaco.caching.CachingFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static java.time.temporal.ChronoUnit.SECONDS;

@Service
@Profile("!local & !tests")
public class TrivoreWebhooksService {

    private static final String EVENT_HANDLING_SUCCEEDED = "succeeded";
    private static final String EVENT_HANDLING_FAILED = "failed";
    private static final String GROUP_CUSTOM_FIELD_MAIL_DOMAINS = "mailDomains";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ObjectMapper objectMapper;
    private final SpringWebClientShim webClient;
    private final String oidcServerUri;
    private final String clientId;
    private final String clientSecret;

    private final LoadingCache<AllGroupsIdentifier, JsonNode> allGroupsCache;
    private final LoadingCache<UserIdentifier, JsonNode> userCache;

    public TrivoreWebhooksService(ObjectMapper objectMapper,
                                  SpringWebClientShim webClient,
                                  @Value("${vaco.trivoreid.server-uri}") String oidcServerUri,
                                  @Value("${vaco.trivoreid.client-id}") String clientId,
                                  @Value("${vaco.trivoreid.client-secret}") String clientSecret) {
        this.objectMapper = objectMapper;
        this.webClient = webClient;
        this.oidcServerUri = oidcServerUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.allGroupsCache = createCache(1, Duration.of(5, SECONDS), new CacheLoader<>() {
            public JsonNode load(AllGroupsIdentifier allGroupsIdentifier) throws Exception {
                return loadALlGroups(allGroupsIdentifier);
            }
        });
        this.userCache = createCache(50, Duration.of(5, SECONDS), new CacheLoader<>() {
            public JsonNode load(UserIdentifier userIdentifier) throws Exception {
                return loadUser(userIdentifier);
            }
        });
    }

    private <K, V> LoadingCache<K, V> createCache(int maximumSize, Duration duration, CacheLoader<K,V> loader) {
        return CacheBuilder.newBuilder()
            .maximumSize(maximumSize)
            .expireAfterWrite(duration)
            .build(loader);
    }


    public String handleWebhook(JsonNode webhookData) {
        return switch (webhookData.get("type").asText()) {
            case "USER_EDIT" -> handleUserEdit(webhookData);
            case "USER_EMAIL_VERIFIED" -> handleUserEmailVerified(webhookData);
            default -> {
                logger.error("Unhandled Webhook type: {}", webhookData.get("type").asText());
                yield EVENT_HANDLING_FAILED;
            }
        };
    }

    private String handleUserEdit(JsonNode webhookData) {
        /*
        {
            "id": "682337f04b041d1dfff95740",
            "type": "USER_EDIT",
            "time": "2025-05-14T07:51:02.685Z",
            "webhookId": "68243ace4b041d1dfff95769",
            "webhookCallId": "68244b664b041d1dfff95779",
            "namespaceCode": "endusers",
            "data": {
                "changedProperties": [
                    "middleName",
                    "searchFields",
                    "searchFields.middleName",
                    "searchFields.names"
                ]
            }
        }
         */
        return EVENT_HANDLING_SUCCEEDED;
    }

    private String handleUserEmailVerified(JsonNode webhookData) {
        /*
        {
            "id": "682337f04b041d1dfff95740",
            "type": "USER_EMAIL_VERIFIED",
            "time": "2025-05-14T07:53:07.716Z",
            "webhookId": "68243ace4b041d1dfff95769",
            "webhookCallId": "68244be34b041d1dfff9577d",
            "namespaceCode": "endusers",
            "data": null
        }
         */
        String userId = webhookData.get("id").asText();
        return fetchUser(userId)
            .map(user -> {
                String id = user.get("id").asText();
                Set<String> newGroups = new HashSet<>();
                for (JsonNode email : user.path("emails")) {
                    if (email.has("verified") && email.get("verified").asBoolean()) {
                        String emailAddress = email.get("address").asText();
                        newGroups.addAll(findMatchingGroups(emailAddress));
                    }
                }
                addUserToGroups(id, newGroups);
                return EVENT_HANDLING_SUCCEEDED;
            }).orElse(EVENT_HANDLING_FAILED);
    }

    private Set<String> findMatchingGroups(String emailAddress) {
        return fetchAllGroups()
            .map(groups -> {
                String userEmailDomain = emailAddress.substring(emailAddress.indexOf("@") + 1);
                Set<String> newGroups = new HashSet<>();
                for (JsonNode group : groups.path("resources")) {
                    Set<String> acceptableGroupMailDomains = new HashSet<>();
                    JsonNode customFields = group.path("customFields");
                    for (JsonNode mailDomain : customFields.path(GROUP_CUSTOM_FIELD_MAIL_DOMAINS)) {
                        acceptableGroupMailDomains.add(mailDomain.textValue());
                    }
                    if (acceptableGroupMailDomains.contains(userEmailDomain)) {
                        String groupId = group.get("id").asText();
                        logger.debug("User email address {} matches {} in group {}", emailAddress, userEmailDomain, groupId);
                        newGroups.add(groupId);
                    }
                }
                return newGroups;
            }).orElse(Set.of());
    }

    private void addUserToGroups(String userId, Set<String> groupIds) {
        ObjectNode patch = objectMapper.createObjectNode();
        logger.debug("Will add user {} to groups {}", userId, groupIds);
        patch.set("memberOf", asArrayNode(groupIds));
        updateUser(userId, patch);
    }

    private String basicAuthenticationHeaderValue() {
        return Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode loadALlGroups(AllGroupsIdentifier allGroupsIdentifier) throws CachingFailureException {
        Optional<JsonNode> allGroups = webClient.executeRequest(
            HttpMethod.GET,
            oidcServerUri + "/api/rest/v1/group",
            Map.of("Authorization", "Basic " + basicAuthenticationHeaderValue(),
                "Content-Type", "application/json")
        );
        if (allGroups.isPresent()) {
            return allGroups.get();
        } else {
            throw new CacheLoadingException("Failed to load user's group membership data for user [" + allGroupsIdentifier + "]");
        }
    }

    private Optional<JsonNode> fetchAllGroups() {
        AllGroupsIdentifier identifier  = new AllGroupsIdentifier("all groups");
        try {
            return Optional.of(allGroupsCache.get(identifier));
        } catch (ExecutionException e) {
            logger.warn("Failed to fetch user's group memberships for identifier [{}]", identifier, e);
            return Optional.empty();
        }
    }


    private Optional<JsonNode> fetchUser(String userId) {
        UserIdentifier identifier  = new UserIdentifier(userId);
        try {
            return Optional.of(userCache.get(identifier));
        } catch (ExecutionException e) {
            logger.warn("Failed to fetch user's group memberships for identifier [{}]", identifier, e);
            return Optional.empty();
        }
    }

    private void updateUser(String userId, JsonNode patch) {
        fetchUser(userId).ifPresent(user -> {
            webClient.executeRequest(
                HttpMethod.PUT,
                oidcServerUri + "/api/rest/v1/user/" + userId,
                Map.of("Authorization", "Basic " + basicAuthenticationHeaderValue(),
                    "Content-Type", "application/json"),
                Optional.of(merge(user, patch))
            ).map(updated -> {
                userCache.put(new UserIdentifier(userId), updated);
                return updated;
            }).orElseThrow(() -> new VacoException("Failed to update user"));
        });
    }

    @VisibleForTesting
    protected static JsonNode merge(JsonNode first, JsonNode... rest) {
        if (rest.length > 0) {
            return merge(deepMerge(first, rest[0]), Arrays.copyOfRange(rest, 1, rest.length));
        } else {
            return first;
        }
    }

    private static JsonNode deepMerge(JsonNode root, JsonNode update) {
        Iterator<String> fieldNames = update.fieldNames();
        while (fieldNames.hasNext()) {

            String fieldName = fieldNames.next();
            JsonNode jsonNode = root.get(fieldName);
            // if field exists and is an embedded object
            if (jsonNode != null && jsonNode.isObject()) {
                deepMerge(jsonNode, update.get(fieldName));
            }
            else {
                if (root instanceof ObjectNode) {
                    // Overwrite field
                    JsonNode value = update.get(fieldName);
                    if (value.isArray()) {
                        ((ArrayNode) root.get(fieldName)).addAll((ArrayNode) value);
                    } else {
                        ((ObjectNode) root).put(fieldName, value);
                    }
                }
            }

        }

        return root;
    }

    private JsonNode loadUser(UserIdentifier userIdentifier) throws CacheLoadingException {
        return webClient.executeRequest(
            HttpMethod.GET,
            oidcServerUri + "/api/rest/v1/user/" + userIdentifier.userIdentifier(),
            Map.of("Authorization", "Basic " + basicAuthenticationHeaderValue(),
                "Content-Type", "application/json")
        ).orElseThrow(() -> new CacheLoadingException("Failed to load user's group membership data for user [" + userIdentifier + "]"));
    }



    private ArrayNode asArrayNode(Set<String> groupIds) {
        ArrayNode array = objectMapper.createArrayNode();
        groupIds.forEach(array::add);
        return array;
    }

    /**
     * Reference to generic all users identifier to be used as cache key.
     * @param staticIdentifier Static identifier to allow for cache hit matching.
     */
    private record AllGroupsIdentifier(String staticIdentifier) {}
    /**
     * Reference to single Trivore group to be used as cache key.
     * @param groupIdentifier Group's id.
     */
    private record GroupIdentifier(String groupIdentifier) {}
    /**
     * Reference to single Trivore user to be used as cache key.
     * @param userIdentifier User's id.
     */
    private record UserIdentifier(String userIdentifier) {}
}
