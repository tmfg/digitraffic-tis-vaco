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

    /**
     * HTTP status code of the receiver's answer. Empty means no response was received.
     */
    Optional<Integer> statusCode();

    /**
     * True only when the receiver answered with a 2xx status. False for any other status and when no response was received.
     */
    default boolean delivered() {
        return statusCode().filter(NotificationResponse::isSuccess).isPresent();
    }

    /**
     * Whether the HTTP status code is in the 2xx range.
     */
    static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }
}
