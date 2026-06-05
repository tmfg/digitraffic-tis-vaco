package fi.digitraffic.tis.vaco.notifications.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = fi.digitraffic.tis.vaco.notifications.model.ImmutableNotification.class)
@JsonDeserialize(builder = fi.digitraffic.tis.vaco.notifications.model.ImmutableNotification.Builder.class)
public interface Notification {
    @Value.Parameter
    @JsonView(DataVisibility.Webhook.class)
    String name();

    @Value.Parameter
    @JsonView(DataVisibility.Webhook.class)
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "name")
    Payload payload();
}
