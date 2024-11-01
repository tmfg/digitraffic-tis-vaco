package fi.digitraffic.tis.vaco.db.model.notifications;

import fi.digitraffic.tis.vaco.notifications.model.SubscriptionType;
import org.immutables.value.Value;

@Value.Immutable
public interface SubscriptionRecord {

    long id();

    SubscriptionType type();

    String publicId();

    long subscriberId();

    long resourceId();
}
