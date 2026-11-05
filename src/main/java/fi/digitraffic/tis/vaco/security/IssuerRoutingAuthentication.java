package fi.digitraffic.tis.vaco.security;

import fi.digitraffic.tis.vaco.configuration.Trivore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Selects the token validation by the issuer of the token. Only the issuers of this map are trusted, and keys of any
 * other issuer are never fetched.
 */
public final class IssuerRoutingAuthentication {

    private static final Duration DISCOVERY_TIMEOUT = Duration.ofSeconds(5);

    private IssuerRoutingAuthentication() {
    }

    /**
     * @param entraIssuer Issuer of the Entra ID tokens
     * @param entraConverter Existing Entra ID converter, which maps the {@code roles} claim to authorities
     * @param entraDecoder Existing Entra ID decoder, reused as is
     * @param trivore Trivore settings. VACO trusts the Trivore issuer only when it has an issuer URI. The roles of a Trivore
     *                client come from the VACO client scopes in its token.
     */
    public static AuthenticationManagerResolver<HttpServletRequest> create(String entraIssuer,
                                                                          JwtDecoder entraDecoder,
                                                                          JwtAuthenticationConverter entraConverter,
                                                                          Trivore trivore) {
        return create(entraIssuer, entraDecoder, entraConverter, trivore, Clock.systemUTC(), DISCOVERY_TIMEOUT);
    }

    static AuthenticationManagerResolver<HttpServletRequest> create(String entraIssuer,
                                                                    JwtDecoder entraDecoder,
                                                                    JwtAuthenticationConverter entraConverter,
                                                                    Trivore trivore,
                                                                    Clock clock,
                                                                    Duration discoveryTimeout) {
        Map<String, AuthenticationManager> managers = new HashMap<>();
        managers.put(entraIssuer, manager(entraDecoder, entraConverter));
        if (trivore != null && trivore.enabled()) {
            managers.put(trivore.issuerUri(),
                manager(trivoreDecoder(trivore, clock, discoveryTimeout), trivoreConverter()));
        }
        return new JwtIssuerAuthenticationManagerResolver(managers::get);
    }

    private static AuthenticationManager manager(JwtDecoder decoder, JwtAuthenticationConverter converter) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(converter);
        return new ProviderManager(provider);
    }

    /**
     * Lazy, so that an unreachable Trivore ID does not prevent VACO from starting.
     */
    private static JwtDecoder trivoreDecoder(Trivore trivore, Clock clock, Duration discoveryTimeout) {
        return new LazyTrivoreDecoder(trivore.issuerUri(), () -> {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(trivore.issuerUri())
                .restOperations(discoveryRestOperations(discoveryTimeout))
                .build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                JwtValidators.createDefaultWithIssuer(trivore.issuerUri()),
                new TrivoreTokenValidator()));
            return decoder;
        }, clock);
    }

    /**
     * Used for the discovery document and for the key set. The connect timeout and the read timeout are both the
     * given timeout.
     */
    private static RestOperations discoveryRestOperations(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        return new RestTemplate(factory);
    }

    private static JwtAuthenticationConverter trivoreConverter() {
        Function<Jwt, Collection<GrantedAuthority>> authorities = jwt -> TrivoreTokenValidator.vacoRoles(jwt)
            .stream()
            .<GrantedAuthority>map(SimpleGrantedAuthority::new)
            .toList();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities::apply);
        return converter;
    }
}
