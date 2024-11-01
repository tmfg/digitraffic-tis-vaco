package fi.digitraffic.tis.vaco.notifications;

import com.fasterxml.jackson.core.type.TypeReference;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.api.model.notifications.CreateSubscriptionRequest;
import fi.digitraffic.tis.vaco.api.model.notifications.ImmutableCreateSubscriptionRequest;
import fi.digitraffic.tis.vaco.notifications.model.Subscription;
import fi.digitraffic.tis.vaco.notifications.model.SubscriptionType;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubscriptionsControllerSystemTests extends SpringBootIntegrationTestBase {

    private final TypeReference<Resource<Subscription>> subscriptionResourceType = new TypeReference<>() {};

    private final TypeReference<Resource<List<Subscription>>> subscriptionListResourceType = new TypeReference<>() {};

    @Test
    void canCreateNewSubscription() throws Exception {
        String subscriber = Constants.FINTRAFFIC_BUSINESS_ID;
        String resource = Constants.PUBLIC_VALIDATION_TEST_ID;

        Resource<Subscription> createdSubscription = createSubscription(SubscriptionType.WEBHOOK, subscriber, resource);
        try {
            assertAll("Created subscription matches with request",
                () -> assertThat(createdSubscription.data().subscriber().businessId(), equalTo(subscriber)),
                () -> assertThat(createdSubscription.data().resource().businessId(), equalTo(resource)));
        } finally {
            deleteSubscription(createdSubscription.data().publicId());
        }
    }

    @Test
    void canListCompanySubscriptions() throws Exception {
        String subscriber = Constants.FINTRAFFIC_BUSINESS_ID;
        String resource = Constants.PUBLIC_VALIDATION_TEST_ID;

        Resource<List<Subscription>> noSubscriptions = listSubscriptions(subscriber);
        assertThat("There shouldn't be any subscriptions at this point.", noSubscriptions.data().isEmpty(), equalTo(true));

        Resource<Subscription> createdSubscription = createSubscription(SubscriptionType.WEBHOOK, subscriber, resource);

        try {
            Resource<List<Subscription>> createdSubscriptions = listSubscriptions(Constants.FINTRAFFIC_BUSINESS_ID);
            assertThat(
                "There should be a single subscriptions at this point.",
                Streams.collect(createdSubscriptions.data(), this::simpleSubscription),
                equalTo(List.of(new SimpleSubscription(SubscriptionType.WEBHOOK, subscriber, resource))));
        } finally {
            deleteSubscription(createdSubscription.data().publicId());
        }
    }

    private Resource<Subscription> createSubscription(SubscriptionType type, String subscriber, String resource) throws Exception {
        CreateSubscriptionRequest createRequest = ImmutableCreateSubscriptionRequest.builder()
            .type(type)
            .subscriber(subscriber)
            .resource(resource)
            .build();

        MvcResult createResponse = apiCall(post("/v1/subscriptions").content(toJson(createRequest)))
            .andExpect(status().isCreated())
            .andReturn();
        return apiResponse(createResponse, subscriptionResourceType);
    }

    private void deleteSubscription(String publicId) throws Exception {
        MvcResult createResponse = apiCall(delete("/v1/subscriptions/" + publicId))
            .andExpect(status().isNoContent())
            .andReturn();
    }

    private Resource<List<Subscription>> listSubscriptions(String subscriber) throws Exception {
        MvcResult listResponse = apiCall(get("/v1/subscriptions").param("subscriber", subscriber))
            .andExpect(status().isOk())
            .andReturn();
        return apiResponse(listResponse, subscriptionListResourceType);
    }

    private SimpleSubscription simpleSubscription(Subscription subscription) {
        return new SimpleSubscription(subscription.type(), subscription.subscriber().businessId(), subscription.resource().businessId());
    }

    /**
     * Helper for test assertions.
     */
    private record SimpleSubscription(SubscriptionType subscriptionType, String subscriber, String resource) {
    }
}
