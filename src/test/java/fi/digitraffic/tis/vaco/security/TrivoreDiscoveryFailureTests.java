package fi.digitraffic.tis.vaco.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import fi.digitraffic.tis.vaco.configuration.Trivore;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Trivore ID discovery that fails and recovers. The Trivore ID issuer is a local HTTP server whose discovery
 * document can be switched off, and the clock is moved by hand.
 */
class TrivoreDiscoveryFailureTests {

    private static final String CLIENT = "0374686053962913";
    private static final String ENTRA_ISSUER = "https://entra.example.test/v2.0";
    private static final String PING = "/v1/ping";
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";
    private static final Clock SYSTEM_CLOCK = Clock.systemUTC();
    private static final Duration DISCOVERY_TIMEOUT = Duration.ofSeconds(5);

    private HttpServer server;
    private String issuer;
    private RSAKey key;
    private final AtomicBoolean available = new AtomicBoolean(false);
    private final AtomicInteger discoveryHits = new AtomicInteger();
    private final MutableClock clock = new MutableClock();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws IOException, JOSEException {
        key = new RSAKeyGenerator(2048).keyID("trivore-key").generate();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        issuer = "http://localhost:" + server.getAddress().getPort() + "/trivore";
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            boolean discovery = path.contains(".well-known");
            if (discovery) {
                discoveryHits.incrementAndGet();
            }
            if (!available.get() || (discovery && !path.equals("/trivore/.well-known/openid-configuration"))) {
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
                return;
            }
            String body = discovery
                ? "{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + issuer + "/jwks\"}"
                : new JWKSet(key.toPublicJWK()).toString();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        Trivore trivore = new Trivore(issuer);
        AuthenticationManagerResolver<HttpServletRequest> resolver = IssuerRoutingAuthentication.create(
            ENTRA_ISSUER, mock(JwtDecoder.class), new JwtAuthenticationConverter(), trivore, clock, DISCOVERY_TIMEOUT);
        mockMvc = MockMvcBuilders.standaloneSetup(new Endpoint())
            .addFilters(new BearerTokenAuthenticationFilter(resolver))
            .build();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @RestController
    static class Endpoint {
        @GetMapping(PING)
        String ping() {
            return "pong";
        }
    }

    @Test
    void unreachableTrivoreIdAnswers401() throws Exception {
        mockMvc.perform(get(PING).header(AUTHORIZATION, BEARER + token()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void failedDiscoveryIsNotRepeatedInsideTheRetryDelay() throws Exception {
        String token = token();
        callIgnoringFailure(token);
        int afterFirst = discoveryHits.get();
        assertThat(afterFirst, greaterThan(0));

        clock.advance(Duration.ofMillis(900));
        callIgnoringFailure(token);

        assertThat(discoveryHits.get(), equalTo(afterFirst));
    }

    private void callIgnoringFailure(String token) throws Exception {
        try {
            mockMvc.perform(get(PING).header(AUTHORIZATION, BEARER + token));
        } catch (ServletException | RuntimeException _) {
            // the status is asserted by another test, this one counts discovery requests
        }
    }

    @Test
    void discoveryRunsAgainAfterTheRetryDelayAndTheDecoderIsKept() throws Exception {
        String token = token();
        mockMvc.perform(get(PING).header(AUTHORIZATION, BEARER + token))
            .andExpect(status().isUnauthorized());

        available.set(true);
        clock.advance(Duration.ofMillis(1001));
        mockMvc.perform(get(PING).header(AUTHORIZATION, BEARER + token))
            .andExpect(status().isOk());

        int afterRecovery = discoveryHits.get();
        mockMvc.perform(get(PING).header(AUTHORIZATION, BEARER + token))
            .andExpect(status().isOk());
        assertThat(discoveryHits.get(), equalTo(afterRecovery));
    }

    @Test
    void discoveryThatAnswersAfterTheReadTimeoutAnswers401() throws Exception {
        HttpServer slow = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        ExecutorService slowExecutor = Executors.newCachedThreadPool();
        CountDownLatch release = new CountDownLatch(1);
        slow.setExecutor(slowExecutor);
        String slowIssuer = "http://localhost:" + slow.getAddress().getPort() + "/trivore";
        slow.createContext("/", exchange -> {
            try {
                // answers only after the client read timeout, or when the test releases the latch
                release.await(1, TimeUnit.SECONDS);
                String body = exchange.getRequestURI().getPath().contains(".well-known")
                    ? "{\"issuer\":\"" + slowIssuer + "\",\"jwks_uri\":\"" + slowIssuer + "/jwks\"}"
                    : new JWKSet(key.toPublicJWK()).toString();
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            } catch (IOException _) {
                // the client has given up
            } finally {
                exchange.close();
            }
        });
        slow.start();
        try {
            Trivore trivore = new Trivore(slowIssuer);
            AuthenticationManagerResolver<HttpServletRequest> resolver = IssuerRoutingAuthentication.create(
                ENTRA_ISSUER, mock(JwtDecoder.class), new JwtAuthenticationConverter(), trivore, clock,
                Duration.ofMillis(200));
            MockMvc slowMvc = MockMvcBuilders.standaloneSetup(new Endpoint())
                .addFilters(new BearerTokenAuthenticationFilter(resolver))
                .build();
            String slowToken = token(slowIssuer);

            slowMvc.perform(get(PING).header(AUTHORIZATION, BEARER + slowToken))
                .andExpect(status().isUnauthorized());
        } finally {
            release.countDown();
            slow.stop(0);
            slowExecutor.shutdownNow();
        }
    }

    private String token() throws JOSEException {
        return token(issuer);
    }

    private String token(String tokenIssuer) throws JOSEException {
        Instant now = Instant.now(SYSTEM_CLOCK);
        SignedJWT jwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
            new JWTClaimsSet.Builder()
                .issuer(tokenIssuer)
                .subject(CLIENT)
                .audience(List.of(CLIENT))
                .claim("scope", "openid vaco.apiuser")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));

        void advance(Duration duration) {
            now.updateAndGet(i -> i.plus(duration));
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
