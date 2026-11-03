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
import fi.digitraffic.tis.vaco.configuration.TrivoreClient;
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
import java.util.Set;
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

        Trivore trivore = new Trivore(issuer, List.of(new TrivoreClient(CLIENT, Set.of("vaco.apiuser"))));
        AuthenticationManagerResolver<HttpServletRequest> resolver = IssuerRoutingAuthentication.create(
            ENTRA_ISSUER, mock(JwtDecoder.class), new JwtAuthenticationConverter(), trivore, clock);
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
        @GetMapping("/v1/ping")
        String ping() {
            return "pong";
        }
    }

    @Test
    void unreachableTrivoreIdAnswers401() throws Exception {
        mockMvc.perform(get("/v1/ping").header("Authorization", "Bearer " + token()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void failedDiscoveryIsNotRepeatedInsideTheRetryWindow() throws Exception {
        String token = token();
        callIgnoringFailure(token);
        int afterFirst = discoveryHits.get();
        assertThat(afterFirst, greaterThan(0));

        clock.advance(Duration.ofSeconds(29));
        callIgnoringFailure(token);

        assertThat(discoveryHits.get(), equalTo(afterFirst));
    }

    private void callIgnoringFailure(String token) throws Exception {
        try {
            mockMvc.perform(get("/v1/ping").header("Authorization", "Bearer " + token));
        } catch (ServletException | RuntimeException _) {
            // the status is asserted by another test, this one counts discovery requests
        }
    }

    @Test
    void discoveryRunsAgainAfterTheRetryWindowAndTheDecoderIsKept() throws Exception {
        String token = token();
        mockMvc.perform(get("/v1/ping").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());

        available.set(true);
        clock.advance(Duration.ofSeconds(31));
        mockMvc.perform(get("/v1/ping").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        int afterRecovery = discoveryHits.get();
        mockMvc.perform(get("/v1/ping").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        assertThat(discoveryHits.get(), equalTo(afterRecovery));
    }

    private String token() throws JOSEException {
        Instant now = Instant.now();
        SignedJWT jwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
            new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(CLIENT)
                .audience(List.of(CLIENT))
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
