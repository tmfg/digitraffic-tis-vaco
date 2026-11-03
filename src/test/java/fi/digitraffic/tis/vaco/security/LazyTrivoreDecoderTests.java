package fi.digitraffic.tis.vaco.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LazyTrivoreDecoderTests {

    @Test
    void concurrentFirstRequestsRunDiscoveryOnce() throws Exception {
        AtomicInteger discoveries = new AtomicInteger();
        Jwt jwt = mock(Jwt.class);
        JwtDecoder delegate = mock(JwtDecoder.class);
        when(delegate.decode("t")).thenReturn(jwt);
        LazyTrivoreDecoder decoder = new LazyTrivoreDecoder("issuer", () -> {
            discoveries.incrementAndGet();
            try {
                Thread.sleep(100);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
            return delegate;
        }, Clock.systemUTC());

        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<Jwt>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return decoder.decode("t");
                }));
            }
            start.countDown();
            for (Future<Jwt> result : results) {
                assertThat(result.get(), equalTo(jwt));
            }
        }

        assertThat(discoveries.get(), equalTo(1));
    }

    @Test
    void secondAttemptRunsAtOnceWhenTheFirstFails() {
        AtomicInteger discoveries = new AtomicInteger();
        Jwt jwt = mock(Jwt.class);
        JwtDecoder delegate = mock(JwtDecoder.class);
        when(delegate.decode("t")).thenReturn(jwt);
        LazyTrivoreDecoder decoder = new LazyTrivoreDecoder("issuer", () -> {
            if (discoveries.incrementAndGet() == 1) {
                throw new IllegalStateException("connection reset");
            }
            return delegate;
        }, Clock.systemUTC());

        assertThat(decoder.decode("t"), equalTo(jwt));
        assertThat(discoveries.get(), equalTo(2));
    }

    @Test
    void delayDoublesAfterEachFailedRequestAndStopsAtFiveMinutes() {
        AtomicInteger discoveries = new AtomicInteger();
        MutableClock clock = new MutableClock();
        LazyTrivoreDecoder decoder = new LazyTrivoreDecoder("issuer", () -> {
            discoveries.incrementAndGet();
            throw new IllegalStateException("down");
        }, clock);

        long[] delaysInSeconds = {1, 2, 4, 8, 16, 32, 64, 128, 256, 300, 300};
        int expected = 0;
        for (long delay : delaysInSeconds) {
            assertThrows(BadJwtException.class, () -> decoder.decode("t"));
            expected += 2;
            assertThat(discoveries.get(), equalTo(expected));

            clock.advance(Duration.ofSeconds(delay).minusMillis(1));
            assertThrows(BadJwtException.class, () -> decoder.decode("t"));
            assertThat(discoveries.get(), equalTo(expected));

            clock.advance(Duration.ofMillis(1));
        }
    }

    @Test
    void requestsInsideTheDelayGetSeparateExceptionsWithTheLastCause() {
        IllegalStateException cause = new IllegalStateException("down");
        LazyTrivoreDecoder decoder = new LazyTrivoreDecoder("issuer", () -> {
            throw cause;
        }, new MutableClock());

        assertThrows(BadJwtException.class, () -> decoder.decode("t"));
        BadJwtException first = assertThrows(BadJwtException.class, () -> decoder.decode("t"));
        BadJwtException second = assertThrows(BadJwtException.class, () -> decoder.decode("t"));

        assertThat(first == second, equalTo(false));
        assertThat(first.getCause(), equalTo(cause));
        assertThat(second.getCause(), equalTo(cause));
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void failureCarriesTheCauseAndAMessageWithoutTheToken() {
        IllegalStateException cause = new IllegalStateException("unreachable");
        LazyTrivoreDecoder decoder = new LazyTrivoreDecoder("issuer", () -> {
            throw cause;
        }, Clock.systemUTC());

        BadJwtException e = assertThrows(BadJwtException.class, () -> decoder.decode("secret-token"));

        assertThat(e.getMessage(), equalTo("Trivore ID is not available"));
        assertThat(e.getCause(), equalTo(cause));
    }
}
