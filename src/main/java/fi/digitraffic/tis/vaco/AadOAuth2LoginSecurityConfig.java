package fi.digitraffic.tis.vaco;

import fi.digitraffic.tis.vaco.configuration.Trivore;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.security.ApiAccessLogFilter;
import fi.digitraffic.tis.vaco.security.IssuerRoutingAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@ConditionalOnProperty(value = "spring.cloud.azure.active-directory.enabled", havingValue = "true")
public class AadOAuth2LoginSecurityConfig {

    private static final Logger REJECTION_LOGGER = LoggerFactory.getLogger(AadOAuth2LoginSecurityConfig.class);

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           @Qualifier("appCorsConfiguration") CorsConfigurationSource corsConfigurationSource,
                                           JwtDecoder entraJwtDecoder,
                                           @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String entraIssuerUri,
                                           VacoProperties vacoProperties) {
        // session management is set to stateless as JWT tokens themselves are stateless
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.authorizeHttpRequests(auth ->
            // public endpoints with no authentication
            auth.requestMatchers(
                "/health/**",                // load balancer health endpoint
                "/ui/bootstrap/**",          // frontend bootstrapping endpoint
                "/badge/**",                 // status badges
                "/v3/api-docs/**",           // Openapi docs
                "/swagger-ui/**",            // Swagger docs
                // magic link exceptions:
                "/ui/processing-results/**", // Entry processing result page may be accessed with magic link
                "/ui/entries/*/state",       // entry state checks its own permissions internally
                "/static/**",                // allow unauthenticated access to static resources for testing
                "/v1/webhooks",              // webhooks endpoint is open to all for now TODO: add shared secret
                "/public/**"                 // explicitly public data, e.g. organisations loaded by RAE at startup
                ).permitAll()
                // private endpoints (=everything else)
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 ->
                oauth2.authenticationManagerResolver(IssuerRoutingAuthentication.create(
                    entraIssuerUri,
                    entraJwtDecoder,
                    overridingGrantedAuthoritiesConverter(),
                    vacoProperties.trivore())));

        // enable CORS
        http.cors(cors -> cors.configurationSource(corsConfigurationSource));
        // and disable CSRF
        http.csrf(AbstractHttpConfigurer::disable);

        // Also sees the 401 that the bearer filter writes. Created here and not a bean on purpose, see the class.
        Trivore trivore = vacoProperties.trivore();
        http.addFilterBefore(
            new ApiAccessLogFilter(
                entraIssuerUri,
                vacoProperties.azureAd().tenantId(),
                trivore != null && trivore.enabled() ? trivore.issuerUri() : null),
            BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Use `roles` claims as granted authorities. Funnily enough, the AAD integration should already do this on its own
     * using configuration from `application.properties` but alas, it does not.
     * @return JwtAuthenticationConverter which uses `roles` claim for authorities.
     */
    private static JwtAuthenticationConverter overridingGrantedAuthoritiesConverter() {
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        jwtGrantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
        jwtGrantedAuthoritiesConverter.setAuthorityPrefix("");
        authenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);
        return authenticationConverter;
    }

    @Bean
    @Qualifier("appCorsConfiguration")
    public CorsConfigurationSource corsConfigurationSource(VacoProperties vacoProperties) {
        String uiBaseUrl = "local".equals(vacoProperties.environment())
            ? "http://localhost:5173"
            : vacoProperties.baseUrl();
        logger.info("Setting CORS configuration for {}", uiBaseUrl);
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(uiBaseUrl));
        configuration.setAllowedMethods(List.of("OPTIONS", "GET", "POST", "DELETE", "PUT"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String jwkIssuerUri,
                          @Value("${vaco.azure-ad.client-id}") String clientId) {
        NimbusJwtDecoder jwtDecoder = JwtDecoders.fromIssuerLocation(jwkIssuerUri);
        // Replaces the default validators, so expiry, not-before, issuer and audience are all listed here.
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(jwkIssuerUri),
            new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(clientId)));
        jwtDecoder.setJwtValidator(logRejections(validator));
        return jwtDecoder;
    }

    /**
     * Logs each rejected token with its failed checks and a fixed set of claims. The token itself is never logged.
     */
    private static OAuth2TokenValidator<Jwt> logRejections(OAuth2TokenValidator<Jwt> delegate) {
        return token -> {
            OAuth2TokenValidatorResult result = delegate.validate(token);
            if (result.hasErrors()) {
                REJECTION_LOGGER.info("Rejected JWT: errors={}, iss={}, aud={}, azp={}, exp={}",
                    result.getErrors().stream().map(OAuth2Error::getDescription).toList(),
                    token.getClaims().get(JwtClaimNames.ISS),
                    token.getClaims().get(JwtClaimNames.AUD),
                    token.getClaims().get("azp"),
                    token.getClaims().get(JwtClaimNames.EXP));
            }
            return result;
        };
    }
}
