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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LazyTrivoreDecoderTests {

    private static final String ISSUER = "issuer";
    private static final String TOKEN = "t";

    @Test
    void concurrentFirstRequestsRunDiscoveryOnce() throws Exception {
        int threads = 8;
        AtomicInteger discoveries = new AtomicInteger();
        CountDownLatch allCalling = new CountDownLatch(threads);
        Jwt jwt = mock(Jwt.class);
        JwtDecoder delegate = mock(JwtDecoder.class);
        when(delegate.decode(TOKEN)).thenReturn(jwt);
        LazyTrivoreDecoder decoder = new LazyTrivoreDecoder(ISSUER, () -> {
            discoveries.incrementAndGet();
            try {
                // discovery stays open until every thread is about to call the decoder
                allCalling.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
            return delegate;
        }, Clock.systemUTC());

        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<Jwt>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    allCalling.countDown();
                    return decoder.decode(TOKEN);
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
        when(delegate.decode(TOKEN)).thenReturn(jwt);
        LazyTrivoreDecoder decoder = new LazyTrivoreDecoder(ISSUER, () -> {
            if (discoveries.incrementAndGet() == 1) {
                throw new IllegalStateException("connection reset");
            }
            return delegate;
        }, Clock.systemUTC());

        assertThat(decoder.decode(TOKEN), equalTo(jwt));
        assertThat(discoveries.get(), equalTo(2));
    }

    @Test
    void delayDoublesAfterEachFailedRequestAndStopsAtFiveMinutes() {
        AtomicInteger discoveries = new AtomicInteger();
        MutableClock clock = new MutableClock();
        LazyTrivoreDecoder decoder = new LazyTrivoreDecoder(ISSUER, () -> {
            discoveries.incrementAndGet();
            throw new IllegalStateException("down");
        }, clock);

        long[] delaysInSeconds = {1, 2, 4, 8, 16, 32, 64, 128, 256, 300, 300};
        int expected = 0;
        for (long delay : delaysInSeconds) {
            assertThrows(BadJwtException.class, () -> decoder.decode(TOKEN));
            expected += 2;
            assertThat(discoveries.get(), equalTo(expected));

            clock.advance(Duration.ofSeconds(delay).minusMillis(1));
            assertThrows(BadJwtException.class, () -> decoder.decode(TOKEN));
            assertThat(discoveries.get(), equalTo(expected));

            clock.advance(Duration.ofMillis(1));
        }
    }

    @Test
    void requestsInsideTheDelayGetSeparateExceptionsWithTheLastCause() {
        IllegalStateException cause = new IllegalStateException("down");
        LazyTrivoreDecoder decoder = new LazyTrivoreDecoder(ISSUER, () -> {
            throw cause;
        }, new MutableClock());

        assertThrows(BadJwtException.class, () -> decoder.decode(TOKEN));
        BadJwtException first = assertThrows(BadJwtException.class, () -> decoder.decode(TOKEN));
        BadJwtException second = assertThrows(BadJwtException.class, () -> decoder.decode(TOKEN));

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
        LazyTrivoreDecoder decoder = new LazyTrivoreDecoder(ISSUER, () -> {
            throw cause;
        }, Clock.systemUTC());

        BadJwtException e = assertThrows(BadJwtException.class, () -> decoder.decode("secret-token"));

        assertThat(e.getMessage(), equalTo("Trivore ID is not available"));
        assertThat(e.getCause(), equalTo(cause));
    }
}
