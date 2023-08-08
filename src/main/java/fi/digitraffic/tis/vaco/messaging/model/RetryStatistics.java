package fi.digitraffic.tis.vaco.messaging.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableRetryStatistics.class)
@JsonDeserialize(as = ImmutableRetryStatistics.class)
public interface RetryStatistics {
    @Value.Default
    default int tryNumber() {
        return 1;
    }

    @Value.Parameter
    int maxRetries();
}
