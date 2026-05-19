package fi.digitraffic.tis.vaco.messaging.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableRetryStatistics.class)
@JsonDeserialize(builder = ImmutableRetryStatistics.Builder.class)
public interface RetryStatistics {
    @Value.Default
    default int tryNumber() {
        return 1;
    }

    @Value.Parameter
    int maxRetries();
}
