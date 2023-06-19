package fi.digitraffic.tis.vaco.messaging.model;

import org.immutables.value.Value;

@Value.Immutable
public interface RetryStatistics {
    @Value.Parameter
    int retryCount();

    @Value.Parameter
    int maxRetries();
}
