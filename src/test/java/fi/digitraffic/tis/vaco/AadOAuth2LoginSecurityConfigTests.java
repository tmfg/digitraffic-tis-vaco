package fi.digitraffic.tis.vaco;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the production Entra security configuration against a local issuer that serves an OIDC discovery document
 * and a JWKS. Tokens are signed with the JWKS key, so only the claims differ between the cases.
 */
@ExtendWith(OutputCaptureExtension.class)
@WebMvcTest(controllers = AadOAuth2LoginSecurityConfigTests.ProbeController.class)
@Import({
    AadOAuth2LoginSecurityConfig.class,
    AadOAuth2LoginSecurityConfigTests.Config.class,
    AadOAuth2LoginSecurityConfigTests.ProbeController.class})
class AadOAuth2LoginSecurityConfigTests {

    private static final String CLIENT_ID = "57c1b8a0-f33e-4e47-840d-8c180d933c41";
    private static final String AUTHORIZED_PARTY = "11111111-2222-3333-4444-555555555555";
    private static final RSAKey KEY = generateKey();
    private static final RSAKey OTHER_KEY = generateKey();
    private static final HttpServer ISSUER_SERVER = startIssuer();
    private static final String ISSUER = "http://localhost:" + ISSUER_SERVER.getAddress().getPort() + "/tenant/v2.0";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.azure.active-directory.enabled", () -> true);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("vaco.azure-ad.client-id", () -> CLIENT_ID);
        registry.add("vaco.environment", () -> "local");
        registry.add("vaco.context-url", () -> "http://localhost");
        registry.add("vaco.base-url", () -> "http://localhost");
        registry.add("vaco.s3-processing-bucket", () -> "test");
        registry.add("vaco.s3-packages-bucket", () -> "test");
    }

    @AfterAll
    static void stopIssuer() {
        ISSUER_SERVER.stop(0);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void t1ExpiredTokenIsRejected() throws Exception {
        call("/probe", builder().expiresAt(Instant.now().minus(5, ChronoUnit.MINUTES)).build())
            .andExpect(status().isUnauthorized());
    }

    @Test
    void t2ForeignIssuerIsRejected() throws Exception {
        call("/probe", builder().issuer("https://attacker.example/").build())
            .andExpect(status().isUnauthorized());
    }

    @Test
    void t3ForeignAudienceIsRejected() throws Exception {
        call("/probe", builder().audience("api://some-other-app").build())
            .andExpect(status().isUnauthorized());
    }

    @Test
    void t4ValidTokenIsAccepted() throws Exception {
        call("/probe", builder().build()).andExpect(status().isOk());
    }

    @Test
    void t5TokenSignedWithUnknownKeyIsRejected() throws Exception {
        call("/probe", builder().key(OTHER_KEY).build()).andExpect(status().isUnauthorized());
    }

    @Test
    void t6TokenNotYetValidIsRejected() throws Exception {
        call("/probe", builder().notBefore(Instant.now().plus(5, ChronoUnit.MINUTES)).build())
            .andExpect(status().isUnauthorized());
    }

    @Test
    void t7TokenWithoutAudienceIsRejected() throws Exception {
        call("/probe", builder().audience(null).build()).andExpect(status().isUnauthorized());
    }

    @Test
    void t8RolesClaimMapsToAuthorities() throws Exception {
        call("/admin", builder().roles(List.of("vaco.admin")).build()).andExpect(status().isOk());
        call("/admin", builder().build()).andExpect(status().isForbidden());
    }

    @Test
    void t9ContextHasOneDecoderAndNoValidatorBeans() {
        assertEquals(1, applicationContext.getBeansOfType(JwtDecoder.class).size());
        assertEquals(0, applicationContext.getBeansOfType(OAuth2TokenValidator.class).size());
        assertEquals(0, applicationContext.getBeanNamesForType(
            ResolvableType.forClassWithGenerics(OAuth2TokenValidator.class, Jwt.class)).length);
    }

    @Test
    void t10RejectedTokenIsLoggedWithoutTheTokenValue(CapturedOutput output) throws Exception {
        String token = builder().expiresAt(Instant.now().minus(5, ChronoUnit.MINUTES)).build();
        call("/probe", token).andExpect(status().isUnauthorized());

        String signature = token.substring(token.lastIndexOf('.') + 1);
        String logged = output.getAll();
        assertTrue(logged.contains("Rejected JWT:"), "rejection line missing");
        assertTrue(logged.contains(ISSUER), "iss missing");
        assertTrue(logged.contains(CLIENT_ID), "aud missing");
        assertTrue(logged.contains(AUTHORIZED_PARTY), "azp missing");
        assertFalse(logged.contains(token), "token must not be logged");
        assertFalse(logged.contains(signature), "token signature must not be logged");
    }

    private ResultActions call(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token));
    }

    private static TokenBuilder builder() {
        return new TokenBuilder();
    }

    /**
     * Builds a token shaped like the Entra v2 access token of the VACO UI: {@code typ: JWT}, v2 issuer, bare client id
     * as audience, no {@code cnf} claim.
     */
    private static final class TokenBuilder {
        private RSAKey key = KEY;
        private String issuer = ISSUER;
        private String audience = CLIENT_ID;
        private Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);
        private Instant notBefore = null;
        private List<String> roles = List.of();

        TokenBuilder key(RSAKey key) {
            this.key = key;
            return this;
        }

        TokenBuilder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        TokenBuilder audience(String audience) {
            this.audience = audience;
            return this;
        }

        TokenBuilder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        TokenBuilder notBefore(Instant notBefore) {
            this.notBefore = notBefore;
            return this;
        }

        TokenBuilder roles(List<String> roles) {
            this.roles = roles;
            return this;
        }

        String build() throws Exception {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("probe")
                .claim("ver", "2.0")
                .claim("azp", AUTHORIZED_PARTY)
                .claim("roles", roles)
                .issueTime(Date.from(expiresAt.minus(1, ChronoUnit.HOURS)))
                .expirationTime(Date.from(expiresAt));
            if (audience != null) {
                claims.audience(audience);
            }
            if (notBefore != null) {
                claims.notBeforeTime(Date.from(notBefore));
            }
            SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(key.getKeyID()).build(),
                claims.build());
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        }
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpServer startIssuer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            String base = "http://localhost:" + server.getAddress().getPort() + "/tenant/v2.0";
            String discovery = "{\"issuer\":\"" + base + "\",\"jwks_uri\":\"" + base + "/keys\","
                + "\"subject_types_supported\":[\"public\"],\"id_token_signing_alg_values_supported\":[\"RS256\"]}";
            String jwks = new JWKSet(KEY.toPublicJWK()).toString();
            server.createContext("/tenant/v2.0/.well-known/openid-configuration", ex -> respond(ex, discovery));
            server.createContext("/tenant/v2.0/keys", ex -> respond(ex, jwks));
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @RestController
    static class ProbeController {
        @GetMapping("/probe")
        String probe() {
            return "ok";
        }

        @GetMapping("/admin")
        @PreAuthorize("hasAuthority('vaco.admin')")
        String admin() {
            return "ok";
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(VacoProperties.class)
    static class Config {
    }
}
