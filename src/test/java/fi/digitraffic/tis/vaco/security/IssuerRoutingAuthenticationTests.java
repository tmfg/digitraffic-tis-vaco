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
import fi.digitraffic.tis.vaco.AadOAuth2LoginSecurityConfig;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.Trivore;
import fi.digitraffic.tis.vaco.configuration.TrivoreClient;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the real security configuration against signed tokens. A local HTTP server plays the Entra ID issuer, the
 * Trivore ID issuer and an issuer that VACO does not trust.
 */
@SpringJUnitWebConfig(classes = {
    AadOAuth2LoginSecurityConfig.class,
    IssuerRoutingAuthenticationTests.TestConfig.class})
@TestPropertySource(properties = "spring.cloud.azure.active-directory.enabled=true")
class IssuerRoutingAuthenticationTests {

    private static final String ALLOWLISTED_E2E = "0374686053962913";
    private static final String ALLOWLISTED_CATALOG = "1111111111111111";
    private static final String NOT_ALLOWLISTED = "5946364502883352";

    private static final Map<String, RSAKey> KEYS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> HITS = new ConcurrentHashMap<>();
    private static final HttpServer SERVER;
    private static final String BASE;

    static {
        try {
            for (String name : List.of("entra", "trivore", "foreign")) {
                KEYS.put(name, new RSAKeyGenerator(2048).keyID(name + "-key").generate());
                HITS.put(name, new AtomicInteger());
            }
            SERVER = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            BASE = "http://localhost:" + SERVER.getAddress().getPort();
            SERVER.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String name = path.split("/")[1];
                HITS.get(name).incrementAndGet();
                String body;
                if (path.endsWith("/.well-known/openid-configuration")) {
                    body = "{\"issuer\":\"" + BASE + "/" + name + "\",\"jwks_uri\":\"" + BASE + "/" + name + "/jwks\"}";
                } else {
                    body = new JWKSet(KEYS.get(name).toPublicJWK()).toString();
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            SERVER.start();
        } catch (IOException | JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.azure.active-directory.enabled", () -> "true");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> BASE + "/entra");
    }

    @Configuration
    @EnableWebMvc
    static class TestConfig {
        @Bean
        VacoProperties vacoProperties() {
            VacoProperties base = TestObjects.vacoProperties();
            Trivore trivore = new Trivore(BASE + "/trivore", List.of(
                new TrivoreClient(ALLOWLISTED_E2E, Set.of("vaco.apiuser", "vaco.admin")),
                new TrivoreClient(ALLOWLISTED_CATALOG, Set.of("vaco.apiuser"))));
            return new VacoProperties(base.environment(), base.temporaryDirectory(), base.s3ProcessingBucket(),
                base.s3PackagesBucket(), base.baseUrl(), base.contextUrl(), base.companyNameClaim(), base.aws(),
                base.azureAd(), trivore, base.email(), base.cleanup(), base.msGraph(), base.encryptionKeys(),
                base.stopsAndQuays());
        }

        @Bean
        Endpoints endpoints() {
            return new Endpoints();
        }
    }

    @RestController
    static class Endpoints {
        @GetMapping("/v1/queue/{publicId}")
        @PreAuthorize("hasAuthority('vaco.apiuser')")
        String entry(@PathVariable String publicId) {
            return publicId;
        }

        @PostMapping("/ui/admin/companies")
        @PreAuthorize("hasAuthority('vaco.admin')")
        String createCompany() {
            return "created";
        }

        @GetMapping("/v1/companies/members/{oid}")
        @PreAuthorize("hasAuthority('vaco.apiuser')")
        String members(@PathVariable String oid) {
            return oid;
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void allowlistedTrivoreClientIsAccepted() throws Exception {
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(trivoreToken(ALLOWLISTED_CATALOG))))
            .andExpect(status().isOk());
    }

    @Test
    void trivoreClientNotOnAllowlistIsRejected() throws Exception {
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(trivoreToken(NOT_ALLOWLISTED))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void allowlistedClientWithoutAdminRoleGetsForbiddenOnAdminEndpoint() throws Exception {
        mockMvc.perform(post("/ui/admin/companies").header("Authorization", bearer(trivoreToken(ALLOWLISTED_CATALOG))))
            .andExpect(status().isForbidden());
    }

    @Test
    void allowlistedClientWithAdminRoleReachesAdminEndpoint() throws Exception {
        mockMvc.perform(post("/ui/admin/companies").header("Authorization", bearer(trivoreToken(ALLOWLISTED_E2E))))
            .andExpect(status().isOk());
    }

    @Test
    void expiredTrivoreTokenIsRejected() throws Exception {
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        String token = sign("trivore", BASE + "/trivore", ALLOWLISTED_E2E, List.of(ALLOWLISTED_E2E), past, Map.of());
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(token)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void trivoreTokenWithoutAudienceIsRejected() throws Exception {
        String token = sign("trivore", BASE + "/trivore", ALLOWLISTED_E2E, null, inOneHour(), Map.of());
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(token)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void trivoreTokenWhoseAudienceDoesNotContainSubjectIsRejected() throws Exception {
        String token = sign("trivore", BASE + "/trivore", ALLOWLISTED_E2E, List.of(NOT_ALLOWLISTED), inOneHour(), Map.of());
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(token)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFromUnknownIssuerIsRejectedWithoutFetchingItsKeys() throws Exception {
        int before = HITS.get("foreign").get();
        String token = sign("foreign", BASE + "/foreign", ALLOWLISTED_E2E, List.of(ALLOWLISTED_E2E), inOneHour(), Map.of());
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(token)))
            .andExpect(status().isUnauthorized());
        assertThat(HITS.get("foreign").get(), equalTo(before));
    }

    @Test
    void trivoreTokenSignedWithAnotherKeyIsRejected() throws Exception {
        String token = sign("foreign", BASE + "/trivore", ALLOWLISTED_E2E, List.of(ALLOWLISTED_E2E), inOneHour(), Map.of());
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(token)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void entraTokenGetsAuthoritiesFromRolesClaim() throws Exception {
        String withRole = entraToken(List.of("vaco.apiuser"));
        String withoutRole = entraToken(List.of());
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(withRole)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(withoutRole)))
            .andExpect(status().isForbidden());
    }

    @Test
    void entraTokenStillReachesMembersEndpoint() throws Exception {
        mockMvc.perform(get("/v1/companies/members/some-oid").header("Authorization", bearer(entraToken(List.of("vaco.apiuser")))))
            .andExpect(status().isOk());
    }

    @Test
    void noTokenIsRejected() throws Exception {
        mockMvc.perform(get("/v1/queue/abc")).andExpect(status().isUnauthorized());
    }

    @Test
    void contextHasOneJwtDecoderAndNoTokenValidatorBeans() {
        assertThat(context.getBeansOfType(JwtDecoder.class).keySet().stream().toList(), equalTo(List.of("jwtDecoder")));
        ResolvableType validator = ResolvableType.forClassWithGenerics(OAuth2TokenValidator.class, Jwt.class);
        assertThat(List.of(context.getBeanNamesForType(validator)), empty());
    }

    private static Instant inOneHour() {
        return Instant.now().plus(1, ChronoUnit.HOURS);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String trivoreToken(String clientId) throws JOSEException {
        return sign("trivore", BASE + "/trivore", clientId, List.of(clientId), inOneHour(), Map.of());
    }

    private static String entraToken(List<String> roles) throws JOSEException {
        return sign("entra", BASE + "/entra", "entra-subject", List.of("api-audience"), inOneHour(), Map.of("roles", roles));
    }

    private static String sign(String keyName,
                               String issuer,
                               String subject,
                               List<String> audience,
                               Instant expires,
                               Map<String, Object> extraClaims) throws JOSEException {
        RSAKey key = KEYS.get(keyName);
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(subject)
            .issueTime(Date.from(expires.minus(1, ChronoUnit.HOURS)))
            .expirationTime(Date.from(expires));
        if (audience != null) {
            claims.audience(audience);
        }
        extraClaims.forEach(claims::claim);
        SignedJWT jwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
            claims.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
