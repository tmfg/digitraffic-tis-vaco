package fi.digitraffic.tis.vaco.messaging;

import fi.digitraffic.tis.vaco.VacoException;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.RetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.Retryable;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class SqsListenerBase<M extends Retryable> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final BiConsumer<M, RetryStatistics> retrier;

    protected SqsListenerBase(BiConsumer<M, RetryStatistics> retrier) {
        this.retrier = retrier;
    }

    protected void handle(M message,
                          String messageIdentifier,
                          Acknowledgement acknowledgement,
                          Consumer<M> outOfRetriesHandler) {
        try {
            if (shouldTry(messageIdentifier, message.retryStatistics())) {
                try {
                    runTask(message);
                } catch (VacoException e) {
                    logger.warn("Unhandled exception during message processing, requeuing message for retrying", e);
                    requeueMessage(message);
                }
            } else {
                outOfRetriesHandler.accept(message);
                logger.warn("Job for entry {} ran out of retries, skipping processing", messageIdentifier);
            }
        } catch (Exception e) {
            logger.error("Unexpected fault caught during message bootstrapping!", e);
        } finally {
            acknowledgement.acknowledge();
        }
    }

    protected boolean shouldTry(String entryPublicId, RetryStatistics retryStatistics) {
        if (retryStatistics.tryNumber() > retryStatistics.maxRetries()) {
            logger.warn("Job for entry {} retried too many times! Cancelling processing and marking the job as done...", entryPublicId);
            return false;
        } else {
            logger.debug("Job for entry {} at try {} of {}", entryPublicId, retryStatistics.tryNumber(), retryStatistics.maxRetries());
            return true;
        }
    }

    protected void requeueMessage(M message) {
        retrier.accept(message, incrementTry(message.retryStatistics()));
    }

    protected RetryStatistics incrementTry(RetryStatistics retryStatistics) {
        ImmutableRetryStatistics stats = ImmutableRetryStatistics.copyOf(retryStatistics);
        return stats.withTryNumber(stats.tryNumber() + 1);
    }

    protected abstract void runTask(M message);
}
