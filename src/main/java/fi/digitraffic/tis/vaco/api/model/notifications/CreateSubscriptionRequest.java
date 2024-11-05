package fi.digitraffic.tis.vaco.api.model.notifications;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.notifications.model.SubscriptionType;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableCreateSubscriptionRequest.class)
@JsonDeserialize(as = ImmutableCreateSubscriptionRequest.class)
public interface CreateSubscriptionRequest {

    SubscriptionType type();

    String subscriber();

    String resource();

}
