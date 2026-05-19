package fi.digitraffic.tis.vaco.api.model.notifications;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.notifications.model.SubscriptionType;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableCreateSubscriptionRequest.class)
@JsonDeserialize(builder = ImmutableCreateSubscriptionRequest.Builder.class)
public interface CreateSubscriptionRequest {

    SubscriptionType type();

    String subscriber();

    String resource();

}
