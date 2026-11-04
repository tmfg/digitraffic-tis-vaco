package fi.digitraffic.tis.vaco.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Creates the Trivore ID decoder on the first token and keeps it for the life of the process.
 * <p>
 * A failed discovery runs once more at once, because a typical failure is a connection reset. When the second attempt
 * also fails, the request gets a {@link BadJwtException}, so the caller answers 401. The decoder then runs no
 * discovery for a delay. The delay starts at {@link #INITIAL_DELAY}, doubles after each failed request, and stops
 * at {@link #MAXIMUM_DELAY}. During the delay, each request gets a new exception with the last cause. Discovery runs
 * on the request thread, and requests that arrive meanwhile wait for it.
 */
final class LazyTrivoreDecoder implements JwtDecoder {

    static final Duration INITIAL_DELAY = Duration.ofSeconds(1);
    static final Duration MAXIMUM_DELAY = Duration.ofMinutes(5);

    private static final String UNAVAILABLE = "Trivore ID is not available";
    private static final Logger LOGGER = LoggerFactory.getLogger(LazyTrivoreDecoder.class);

    private final String issuer;
    private final Supplier<JwtDecoder> discovery;
    private final Clock clock;

    private final AtomicReference<JwtDecoder> decoder = new AtomicReference<>();
    private RuntimeException lastCause;
    private Duration nextDelay = INITIAL_DELAY;
    private Instant retryAt = Instant.MIN;

    LazyTrivoreDecoder(String issuer, Supplier<JwtDecoder> discovery, Clock clock) {
        this.issuer = issuer;
        this.discovery = discovery;
        this.clock = clock;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        JwtDecoder ready = decoder.get();
        return (ready != null ? ready : discover()).decode(token);
    }

    private synchronized JwtDecoder discover() {
        JwtDecoder existing = decoder.get();
        if (existing != null) {
            return existing;
        }
        if (Instant.now(clock).isBefore(retryAt)) {
            throw new BadJwtException(UNAVAILABLE, lastCause);
        }
        try {
            return attempt();
        } catch (RuntimeException e) {
            // a typical failure is a connection reset, so the second attempt runs at once
            logFailure(e);
        }
        try {
            return attempt();
        } catch (RuntimeException e) {
            logFailure(e);
            lastCause = e;
            retryAt = Instant.now(clock).plus(nextDelay);
            nextDelay = nextDelay.multipliedBy(2).compareTo(MAXIMUM_DELAY) > 0 ? MAXIMUM_DELAY : nextDelay.multipliedBy(2);
            throw new BadJwtException(UNAVAILABLE, e);
        }
    }

    private JwtDecoder attempt() {
        JwtDecoder created = discovery.get();
        decoder.set(created);
        lastCause = null;
        nextDelay = INITIAL_DELAY;
        return created;
    }

    private void logFailure(RuntimeException e) {
        LOGGER.warn("Discovery of Trivore ID issuer {} failed: {}", issuer, e.getMessage());
    }
}
