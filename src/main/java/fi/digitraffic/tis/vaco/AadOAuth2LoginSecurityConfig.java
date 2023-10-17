package fi.digitraffic.tis.vaco;

import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
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

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/health/**", "/ui/bootstrap/**").permitAll()
                    .anyRequest().authenticated();
            })
            // NOTE: Order is important here! Above is for Azure AD support, below is our extras
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(VacoProperties vacoProperties) {
        String uiBaseUrl = "local".equals(vacoProperties.environment())
            ? "http://localhost:5173"
            : vacoProperties.baseUrl();
        logger.info("Setting CORS configuration for {}", uiBaseUrl);
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(uiBaseUrl));
        configuration.setAllowedMethods(List.of("OPTIONS", "GET", "POST"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public OAuth2TokenValidator<Jwt> issuerClaimValidator(
        @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String jwkIssuerUri) {
        return JwtValidators.createDefaultWithIssuer(jwkIssuerUri);
    }

    @Bean
    public OAuth2TokenValidator<Jwt> audienceClaimValidator(@Value("${vaco.azure-ad.client-id}") String clientId) {
        return new JwtTokenValidator(
            token -> token.getAudience().contains(clientId),
            new OAuth2Error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT));
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String jwkIssuerUri,
                          List<OAuth2TokenValidator<Jwt>> validators) {
        NimbusJwtDecoder jwtDecoder = JwtDecoders.fromIssuerLocation(jwkIssuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(validators);
        jwtDecoder.setJwtValidator(withAudience);
        return jwtDecoder;
    }
}
