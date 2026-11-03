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
import java.util.function.Supplier;

/**
 * Creates the Trivore ID decoder on the first token and keeps it for the life of the process.
 * <p>
 * A failed discovery becomes a {@link BadJwtException}, so the caller answers 401. After a failure, the decoder throws
 * the same exception for {@link #RETRY_DELAY} without running discovery again. Discovery runs on the request thread,
 * and it may wait for a long time when Trivore ID is unreachable.
 */
final class LazyTrivoreDecoder implements JwtDecoder {

    static final Duration RETRY_DELAY = Duration.ofSeconds(30);

    private static final Logger LOGGER = LoggerFactory.getLogger(LazyTrivoreDecoder.class);

    private final String issuer;
    private final Supplier<JwtDecoder> discovery;
    private final Clock clock;

    private volatile JwtDecoder decoder;
    private BadJwtException lastFailure;
    private Instant retryAt = Instant.MIN;

    LazyTrivoreDecoder(String issuer, Supplier<JwtDecoder> discovery, Clock clock) {
        this.issuer = issuer;
        this.discovery = discovery;
        this.clock = clock;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        JwtDecoder ready = decoder;
        return (ready != null ? ready : discover()).decode(token);
    }

    private synchronized JwtDecoder discover() {
        if (decoder != null) {
            return decoder;
        }
        if (Instant.now(clock).isBefore(retryAt)) {
            throw lastFailure;
        }
        try {
            decoder = discovery.get();
            lastFailure = null;
            return decoder;
        } catch (RuntimeException e) {
            LOGGER.warn("Discovery of Trivore ID issuer {} failed: {}", issuer, e.getMessage());
            lastFailure = new BadJwtException("Trivore ID is not available", e);
            retryAt = Instant.now(clock).plus(RETRY_DELAY);
            throw lastFailure;
        }
    }
}
