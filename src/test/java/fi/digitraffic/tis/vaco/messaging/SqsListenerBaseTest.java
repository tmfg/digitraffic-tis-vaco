package fi.digitraffic.tis.vaco.messaging;

import fi.digitraffic.tis.vaco.VacoException;
import fi.digitraffic.tis.vaco.messaging.model.ImmutableRetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.RetryStatistics;
import fi.digitraffic.tis.vaco.messaging.model.Retryable;
import fi.digitraffic.tis.vaco.entries.EntryRepository;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.immutables.value.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.BiConsumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class SqsListenerBaseTest {

    private static final String MESSAGE_IDENTIFIER = "{retryable.message.identifier}";
    private SqsListenerBase<ImmutableRetryableTestType> listener;

    private ImmutableRetryableTestType retryableMessage;
    private ImmutableRetryableTestType receivedMessage;
    private ImmutableRetryableTestType retriedMessage;

    private BiConsumer<ImmutableRetryableTestType, RetryStatistics> retrier;

    @Mock
    private MessagingService messagingService;
    @Mock
    private EntryRepository entryRepository;
    @Mock
    private Acknowledgement acknowledgement;

    @BeforeEach
    void setUp() {
        retrier = (message, stats) -> {
            retriedMessage = message.withRetryStatistics(stats);
        };
        retryableMessage = ImmutableRetryableTestType.builder()
            .retryStatistics(ImmutableRetryStatistics.of(5))
            .build();
        listener = new SqsListenerBase<>(retrier) {
            @Override
            protected void runTask(ImmutableRetryableTestType message) {
                receivedMessage = message;
            }
        };
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(messagingService, entryRepository);
    }

    @Test
    void runsValidationByDefault() {
        listener.handle(retryableMessage, MESSAGE_IDENTIFIER, acknowledgement, (ignored) -> {});
        assertThat(receivedMessage, equalTo(retryableMessage));
    }

    @Test
    void retriesIfProcessingThrowsException() {
        listener = new SqsListenerBase<>(retrier) {
            @Override
            protected void runTask(ImmutableRetryableTestType message) {
                throw new FakeVacoExeption("ba-doom!");
            }
        };
        listener.handle(retryableMessage, MESSAGE_IDENTIFIER, acknowledgement, (ignored) -> {});

        // ...so job is requeued...
        assertThat(retriedMessage, notNullValue());
        /// ...with updated retry count
        assertThat(retriedMessage.retryStatistics().tryNumber(), equalTo(2));
    }

    @Test
    void lastTryIsStillExecutedNormally() {
        RetryStatistics retries = retryableMessage.retryStatistics();
        ImmutableRetryableTestType lastTry = retryableMessage.withRetryStatistics(
            ImmutableRetryStatistics
                .copyOf(retries)
                .withTryNumber(retries.maxRetries()));

        listener.handle(lastTry, MESSAGE_IDENTIFIER, acknowledgement, (ignored) -> {});

        assertThat(receivedMessage, equalTo(lastTry));
    }

    @Test
    void stopsRequeuingIfRetriesAreExhaustedAndMarksJobAsComplete() {
        RetryStatistics retries = retryableMessage.retryStatistics();
        ImmutableRetryableTestType lastTry = retryableMessage.withRetryStatistics(
            ImmutableRetryStatistics
                .copyOf(retries)
                .withTryNumber(retries.maxRetries() + 1)); // technically this is run 6 of 5, so it just skips everything

        listener.handle(lastTry, MESSAGE_IDENTIFIER, acknowledgement, (ignored) -> {});

        // too many retries, do nothing
        assertThat(receivedMessage, nullValue());
        assertThat(retriedMessage, nullValue());
    }

    @Value.Immutable
    public interface RetryableTestType extends Retryable {
    }

    private class FakeVacoExeption extends VacoException {

        public FakeVacoExeption(String message) {
            super(message);
        }
    }
}
