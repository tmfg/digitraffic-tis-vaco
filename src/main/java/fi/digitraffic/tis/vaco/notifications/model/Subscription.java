package fi.digitraffic.tis.vaco.notifications.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.company.model.Company;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableSubscription.class)
@JsonDeserialize(builder = ImmutableSubscription.Builder.class)
public interface Subscription {

    @Value.Parameter
    String publicId();

    @Value.Parameter
    SubscriptionType type();

    @Value.Parameter
    Company subscriber();

    @Value.Parameter
    Company resource();
}
