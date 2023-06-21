package fi.digitraffic.tis.vaco.messaging.model;

/**
 * Shared marker interface for all messages which are expected to be retryable.
 */
public interface Retryable {
    RetryStatistics retryStatistics();
}
