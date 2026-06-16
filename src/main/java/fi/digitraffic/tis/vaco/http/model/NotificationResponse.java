package fi.digitraffic.tis.vaco.http.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.Optional;

@Value.Immutable
@JsonSerialize(as = fi.digitraffic.tis.vaco.http.model.ImmutableNotificationResponse.class)
@JsonDeserialize(as = fi.digitraffic.tis.vaco.http.model.ImmutableNotificationResponse.class)
public interface NotificationResponse {
    Optional<byte[]> response();
}
