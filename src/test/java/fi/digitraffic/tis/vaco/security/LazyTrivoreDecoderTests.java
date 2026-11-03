package fi.digitraffic.tis.vaco.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Clock;
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
