package fi.digitraffic.tis.vaco.security;

import fi.digitraffic.tis.vaco.configuration.Trivore;
import fi.digitraffic.tis.vaco.configuration.TrivoreClient;
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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
     * @param trivore Trivore settings. The Trivore issuer is trusted only when it has at least one client.
     */
    public static AuthenticationManagerResolver<HttpServletRequest> create(String entraIssuer,
                                                                          JwtDecoder entraDecoder,
                                                                          JwtAuthenticationConverter entraConverter,
                                                                          Trivore trivore) {
        return create(entraIssuer, entraDecoder, entraConverter, trivore, Clock.systemUTC());
    }

    static AuthenticationManagerResolver<HttpServletRequest> create(String entraIssuer,
                                                                    JwtDecoder entraDecoder,
                                                                    JwtAuthenticationConverter entraConverter,
                                                                    Trivore trivore,
                                                                    Clock clock) {
        Map<String, AuthenticationManager> managers = new HashMap<>();
        managers.put(entraIssuer, manager(entraDecoder, entraConverter));
        if (trivore != null && trivore.enabled()) {
            managers.put(trivore.issuerUri(), manager(trivoreDecoder(trivore, clock), trivoreConverter(trivore)));
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
    private static JwtDecoder trivoreDecoder(Trivore trivore, Clock clock) {
        Set<String> clientIds = trivore.clients().stream().map(TrivoreClient::clientId).collect(Collectors.toSet());
        return new LazyTrivoreDecoder(trivore.issuerUri(), () -> {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(trivore.issuerUri())
                .restOperations(discoveryRestOperations())
                .build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                JwtValidators.createDefaultWithIssuer(trivore.issuerUri()),
                new TrivoreTokenValidator(clientIds)));
            return decoder;
        }, clock);
    }

    /**
     * Used for the discovery document and for the key set. Both timeouts are 5 s.
     */
    private static RestOperations discoveryRestOperations() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) DISCOVERY_TIMEOUT.toMillis());
        factory.setReadTimeout((int) DISCOVERY_TIMEOUT.toMillis());
        return new RestTemplate(factory);
    }

    private static JwtAuthenticationConverter trivoreConverter(Trivore trivore) {
        Map<String, Set<String>> rolesByClient = new HashMap<>();
        trivore.clients().forEach(c -> rolesByClient.put(c.clientId(), c.roles()));
        Function<Jwt, Collection<GrantedAuthority>> authorities = jwt -> rolesByClient
            .getOrDefault(jwt.getSubject(), Set.of())
            .stream()
            .<GrantedAuthority>map(SimpleGrantedAuthority::new)
            .toList();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities::apply);
        return converter;
    }
}
