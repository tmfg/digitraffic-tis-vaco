package fi.digitraffic.tis.vaco.notifications.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.company.model.Company;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableSubscription.class)
@JsonDeserialize(as = ImmutableSubscription.class)
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
