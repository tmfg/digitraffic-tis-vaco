package fi.digitraffic.tis.vaco.me;

import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import fi.digitraffic.tis.vaco.configuration.Trivore;
import fi.digitraffic.tis.vaco.fintrafficid.FintrafficIdService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class MeServiceRoleTests {

    private final MeService meService = new MeService(
        Mockito.mock(CompanyHierarchyService.class),
        Mockito.mock(FintrafficIdService.class));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt clientCredentialsJwt(String clientId) {
        return new Jwt("token", Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS),
            Map.of("alg", "RS256"), Map.of("sub", clientId, "aud", java.util.List.of(clientId)));
    }

    @Test
    void trivoreClientWithGrantedRoleIsApiUser() {
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(clientCredentialsJwt("client-1"), AuthorityUtils.createAuthorityList("vaco.apiuser")));

        assertThat(meService.isApiUser(), equalTo(true));
        assertThat(meService.isAdmin(), equalTo(false));
    }

    @Test
    void trivoreClientWithAdminRoleIsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(clientCredentialsJwt("client-1"), AuthorityUtils.createAuthorityList("vaco.apiuser", "vaco.admin")));

        assertThat(meService.isAdmin(), equalTo(true));
    }

    @Test
    void rolesClaimWithoutGrantedAuthorityGivesNoRole() {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS),
            Map.of("alg", "RS256"), Map.of("sub", "someone", "roles", java.util.List.of("vaco.admin")));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(meService.isAdmin(), equalTo(false));
    }

    @Test
    void alertTextIsTheTrivoreClientId() {
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(clientCredentialsJwt("client-1"), AuthorityUtils.createAuthorityList("vaco.apiuser")));

        assertThat(meService.alertText(), equalTo("client-1"));
    }

    @Test
    void alertTextIsTheOidOfAnEntraUser() {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS),
            Map.of("alg", "RS256"), Map.of("sub", "subject", "oid", "the-oid"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(meService.alertText(), equalTo("the-oid"));
    }

    @Test
    void alertTextWithoutAuthenticationIsUnauthenticated() {
        assertThat(meService.alertText(), equalTo("unauthenticated"));
    }

    @Test
    void trivoreAllowlistBindsRolesFromCommaSeparatedValue() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
            "vaco.trivore.issuer-uri", "https://issuer.example",
            "vaco.trivore.clients[0].client-id", "client-1",
            "vaco.trivore.clients[0].roles", "vaco.apiuser,vaco.admin")));

        Trivore trivore = binder.bind("vaco.trivore", Trivore.class).get();

        assertThat(trivore.enabled(), equalTo(true));
        assertThat(trivore.clients().get(0).clientId(), equalTo("client-1"));
        assertThat(trivore.clients().get(0).roles(), equalTo(Set.of("vaco.apiuser", "vaco.admin")));
    }

    @Test
    void trivoreWithoutClientsIsDisabled() {
        assertThat(new Trivore("https://issuer.example", null).enabled(), equalTo(false));
        assertThat(new Trivore(null, null).enabled(), equalTo(false));
    }
}
