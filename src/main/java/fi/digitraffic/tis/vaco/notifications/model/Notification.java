package fi.digitraffic.tis.vaco.notifications.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = fi.digitraffic.tis.vaco.notifications.model.ImmutableNotification.class)
@JsonDeserialize(builder = fi.digitraffic.tis.vaco.notifications.model.ImmutableNotification.Builder.class)
public interface Notification {
    @Value.Parameter
    String name();

    @Value.Parameter
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "name")
    Payload payload();
}
