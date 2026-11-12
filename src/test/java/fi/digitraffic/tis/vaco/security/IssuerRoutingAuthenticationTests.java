package fi.digitraffic.tis.vaco.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private static final String E2E_CLIENT = "0374686053962913";
    private static final String CATALOG_CLIENT = "1111111111111111";
    private static final String OTHER_CLIENT = "5946364502883352";
    private static final String APIUSER_SCOPES = "openid vaco.apiuser";
    private static final String ADMIN_SCOPES = "openid vaco.apiuser vaco.admin";
    private static final Map<String, Object> APIUSER_CLAIMS = Map.of("scope", APIUSER_SCOPES);
    private static final String ENTRA_AUDIENCE = "api-audience";

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
        registry.add("vaco.azure-ad.client-id", () -> ENTRA_AUDIENCE);
    }

    @Configuration
    @EnableWebMvc
    static class TestConfig {
        @Bean
        VacoProperties vacoProperties() {
            VacoProperties base = TestObjects.vacoProperties();
            Trivore trivore = new Trivore(BASE + "/trivore");
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

        @GetMapping("/me")
        String me() {
            return "me";
        }

        @GetMapping("/v1/unauthorized")
        String unauthorized() {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        @GetMapping("/v1/boom")
        String boom() {
            throw new IllegalStateException("boom");
        }

        @GetMapping("/badge/{publicId}/{taskName}")
        String badge(@PathVariable String publicId, @PathVariable String taskName) {
            return publicId + taskName;
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
    private ListAppender<ILoggingEvent> accessLog;
    private Logger accessLogger;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        accessLogger = (Logger) LoggerFactory.getLogger(ApiAccessLogFilter.LOGGER_NAME);
        accessLogger.setLevel(Level.INFO);
        accessLog = new ListAppender<>();
        accessLog.start();
        accessLogger.addAppender(accessLog);
    }

    @AfterEach
    void detachAccessLog() {
        accessLogger.detachAppender(accessLog);
    }

    private List<String> accessLines() {
        return accessLog.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void trivoreApplicationTokenWritesOneFullAccessLine() throws Exception {
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(trivoreToken(CATALOG_CLIENT, APIUSER_SCOPES))))
            .andExpect(status().isOk());
        assertThat(accessLines(), hasSize(1));
        assertThat(accessLines().getFirst(), matchesPattern(
            "^GET (/api)?/v1/queue/\\S+ \\d{3} \\d+ms issuer=trivore client=" + CATALOG_CLIENT + " ver=-$"));
    }

    static Stream<Arguments> entraApplicationClaims() {
        return Stream.of(
            Arguments.of(Map.of("azp", "azp-client", "appid", "app-id-client", "ver", "2.0"), "azp-client", "2.0"),
            Arguments.of(Map.of("appid", "app-id-client", "ver", "1.0"), "app-id-client", "1.0"),
            Arguments.of(Map.of("ver", "2.0"), "-", "2.0"),
            Arguments.of(Map.of("azp", "azp-client"), "azp-client", "-"));
    }

    @ParameterizedTest
    @MethodSource("entraApplicationClaims")
    void entraApplicationTokenWritesClientAndVersionFallbacks(Map<String, Object> claims,
                                                              String expectedClient,
                                                              String expectedVersion) throws Exception {
        Map<String, Object> all = new HashMap<>(claims);
        all.put("roles", List.of("vaco.apiuser"));
        String token = sign("entra", BASE + "/entra", "entra-subject", List.of(ENTRA_AUDIENCE), inOneHour(), all);
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(token))).andExpect(status().isOk());
        assertThat(accessLines(), hasSize(1));
        assertThat(accessLines().getFirst(), endsWith(
            " issuer=entra client=" + expectedClient + " ver=" + expectedVersion));
    }

    @Test
    void userTokenWith200AndAnonymousBadgeWriteNoLine() throws Exception {
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(entraUserToken())))
            .andExpect(status().isOk());
        mockMvc.perform(get("/badge/abc/prepare.download")).andExpect(status().isOk());
        assertThat(accessLines(), empty());
    }

    @Test
    void entraV1IssuerTokenIsRejectedAndLoggedWithoutClaimValues() throws Exception {
        String token = sign("entra", "https://sts.windows.net/tenantId/", "v1-subject-sentinel",
            List.of(ENTRA_AUDIENCE), inOneHour(), Map.of("roles", List.of("vaco.apiuser")));
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(token)))
            .andExpect(status().isUnauthorized());
        assertThat(accessLines(), hasSize(1));
        assertThat(accessLines().getFirst(), matchesPattern(
            "^GET \\S+ 401 \\d+ms issuer=entra-v1 client=- ver=-$"));
        assertThat(accessLines().getFirst(), not(containsString("v1-subject-sentinel")));
    }

    @Test
    void unknownIssuerWritesUnknownLabelWithoutRawIssuer() throws Exception {
        String token = sign("foreign", BASE + "/foreign", E2E_CLIENT, List.of(E2E_CLIENT), inOneHour(), APIUSER_CLAIMS);
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(token)))
            .andExpect(status().isUnauthorized());
        assertThat(accessLines(), hasSize(1));
        assertThat(accessLines().getFirst(), endsWith(" issuer=unknown client=- ver=-"));
        assertThat(accessLines().getFirst(), not(containsString("/foreign")));
    }

    @Test
    void missingAndMalformedBearerTokensWriteNoneLabel() throws Exception {
        mockMvc.perform(get("/v1/queue/abc")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", "Bearer not-a-jwt"))
            .andExpect(status().isUnauthorized());
        assertThat(accessLines(), hasSize(2));
        accessLines().forEach(line -> assertThat(line, endsWith(" issuer=none client=- ver=-")));
    }

    @Test
    void trivoreTokenWithoutVacoScopeWritesTrivoreLabelWithoutSubject() throws Exception {
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(trivoreToken(E2E_CLIENT, "openid"))))
            .andExpect(status().isUnauthorized());
        assertThat(accessLines(), hasSize(1));
        assertThat(accessLines().getFirst(), endsWith(" issuer=trivore client=- ver=-"));
        assertThat(accessLines().getFirst(), not(containsString(E2E_CLIENT)));
    }

    @Test
    void userTokenThat401sAfterAuthenticationWritesNoUserClaim() throws Exception {
        String token = sign("entra", BASE + "/entra", "entra-subject", List.of(ENTRA_AUDIENCE), inOneHour(), Map.of(
            "scp", "access_as_user",
            "oid", "user-oid-sentinel",
            "name", "User Name Sentinel",
            "preferred_username", "user.sentinel@example.com"));
        mockMvc.perform(get("/v1/unauthorized").header("Authorization", bearer(token)))
            .andExpect(status().isUnauthorized());
        assertThat(accessLines(), hasSize(1));
        String line = accessLines().getFirst();
        assertThat(line, endsWith(" issuer=entra client=- ver=-"));
        assertThat(line, not(containsString("user-oid-sentinel")));
        assertThat(line, not(containsString("User Name Sentinel")));
        assertThat(line, not(containsString("user.sentinel@example.com")));
    }

    @Test
    void exceptionWritesStatus500AndIsRethrown() throws Exception {
        String token = trivoreToken(E2E_CLIENT, ADMIN_SCOPES);
        assertThrows(ServletException.class,
            () -> mockMvc.perform(get("/v1/boom").header("Authorization", bearer(token))));
        assertThat(accessLines(), hasSize(1));
        assertThat(accessLines().getFirst(), matchesPattern("^GET \\S+ 500 \\d+ms issuer=trivore client=\\d+ ver=-$"));
    }

    @Test
    void controlCharactersInClaimsDoNotSplitTheLine() throws Exception {
        String token = sign("entra", BASE + "/entra", "entra-subject", List.of(ENTRA_AUDIENCE), inOneHour(),
            Map.of("roles", List.of("vaco.apiuser"), "azp", "evil\r\nFAKE line", "ver", "2\n0"));
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(token))).andExpect(status().isOk());
        assertThat(accessLines(), hasSize(1));
        String line = accessLines().getFirst();
        assertThat(line, not(containsString("\r")));
        assertThat(line, not(containsString("\n")));
        assertThat(line, endsWith(" issuer=entra client=evil__FAKE line ver=2_0"));
    }

    @Test
    void lineHasNoQueryStringAndShowsStatus403And404() throws Exception {
        String token = entraToken(List.of());
        mockMvc.perform(get("/v1/queue/abc?secret=query-sentinel").header("Authorization", bearer(token)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/missing").header("Authorization", bearer(token)))
            .andExpect(status().isNotFound());
        assertThat(accessLines(), hasSize(2));
        assertThat(accessLines().get(0), matchesPattern("^GET (/api)?/v1/queue/abc 403 \\d+ms .*"));
        assertThat(accessLines().get(0), not(containsString("query-sentinel")));
        assertThat(accessLines().get(1), matchesPattern("^GET (/api)?/v1/missing 404 \\d+ms .*"));
    }

    private static String entraUserToken() throws JOSEException {
        return sign("entra", BASE + "/entra", "entra-subject", List.of(ENTRA_AUDIENCE), inOneHour(),
            Map.of("scp", "access_as_user", "roles", List.of("vaco.apiuser")));
    }

    @Test
    void trivoreClientWithApiUserScopeIsAccepted() throws Exception {
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(trivoreToken(CATALOG_CLIENT, APIUSER_SCOPES))))
            .andExpect(status().isOk());
    }

    @Test
    void trivoreClientNotKnownToVacoGetsItsRoleFromTheScope() throws Exception {
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(trivoreToken(OTHER_CLIENT, APIUSER_SCOPES))))
            .andExpect(status().isOk());
    }

    @Test
    void apiUserScopeGetsForbiddenOnAdminEndpoint() throws Exception {
        mockMvc.perform(post("/ui/admin/companies").header("Authorization", bearer(trivoreToken(OTHER_CLIENT, APIUSER_SCOPES))))
            .andExpect(status().isForbidden());
    }

    @Test
    void rerunPathRequiresSignIn() throws Exception {
        mockMvc.perform(post("/ui/admin/entries/abc/rerun"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminScopeReachesAdminEndpoint() throws Exception {
        mockMvc.perform(post("/ui/admin/companies").header("Authorization", bearer(trivoreToken(OTHER_CLIENT, ADMIN_SCOPES))))
            .andExpect(status().isOk());
    }

    @Test
    void trivoreTokenWithOnlyOpenidScopeIsRejected() throws Exception {
        mockMvc.perform(get("/me").header("Authorization", bearer(trivoreToken(E2E_CLIENT, "openid"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void trivoreTokenWithoutScopeClaimIsRejected() throws Exception {
        String token = sign("trivore", BASE + "/trivore", E2E_CLIENT, List.of(E2E_CLIENT), inOneHour(), Map.of());
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(token)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void trivoreTokenWithOnlyAForeignVacoScopeIsRejected() throws Exception {
        mockMvc.perform(get("/v1/queue/abc")
                .header("Authorization", bearer(trivoreToken(E2E_CLIENT, "openid vaco.company_admin"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void trivoreRolesClaimGivesNoRole() throws Exception {
        String token = sign("trivore", BASE + "/trivore", E2E_CLIENT, List.of(E2E_CLIENT), inOneHour(),
            Map.of("scope", APIUSER_SCOPES, "roles", List.of("vaco.admin")));
        mockMvc.perform(post("/ui/admin/companies").header("Authorization", bearer(token)))
            .andExpect(status().isForbidden());
    }

    @Test
    void expiredTrivoreTokenIsRejected() throws Exception {
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        String token = sign("trivore", BASE + "/trivore", E2E_CLIENT, List.of(E2E_CLIENT), past, APIUSER_CLAIMS);
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(token)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void trivoreTokenWithoutAudienceIsRejected() throws Exception {
        String token = sign("trivore", BASE + "/trivore", E2E_CLIENT, null, inOneHour(), APIUSER_CLAIMS);
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(token)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void trivoreTokenWhoseAudienceDoesNotContainSubjectIsRejected() throws Exception {
        String token = sign("trivore", BASE + "/trivore", E2E_CLIENT, List.of(OTHER_CLIENT), inOneHour(), APIUSER_CLAIMS);
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(token)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFromUnknownIssuerIsRejectedWithoutFetchingItsKeys() throws Exception {
        int before = HITS.get("foreign").get();
        String token = sign("foreign", BASE + "/foreign", E2E_CLIENT, List.of(E2E_CLIENT), inOneHour(), APIUSER_CLAIMS);
        mockMvc.perform(get("/v1/queue/abc").header("Authorization", bearer(token)))
            .andExpect(status().isUnauthorized());
        assertThat(HITS.get("foreign").get(), equalTo(before));
    }

    @Test
    void trivoreTokenSignedWithAnotherKeyIsRejected() throws Exception {
        String token = sign("foreign", BASE + "/trivore", E2E_CLIENT, List.of(E2E_CLIENT), inOneHour(), APIUSER_CLAIMS);
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

    private static String trivoreToken(String clientId, String scope) throws JOSEException {
        return sign("trivore", BASE + "/trivore", clientId, List.of(clientId), inOneHour(), Map.of("scope", scope));
    }

    private static String entraToken(List<String> roles) throws JOSEException {
        return sign("entra", BASE + "/entra", "entra-subject", List.of(ENTRA_AUDIENCE), inOneHour(), Map.of("roles", roles));
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
