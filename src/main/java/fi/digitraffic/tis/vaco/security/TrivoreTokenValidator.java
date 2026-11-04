package fi.digitraffic.tis.vaco.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Accepts a Trivore ID token only when it was issued for its own client and carries at least one VACO client scope.
 * <p>
 * Trivore client scopes {@code vaco.apiuser} and {@code vaco.admin} map to the VACO roles of the same name. Every
 * other scope value is ignored.
 * <p>
 * This is deliberately not a Spring bean. A bean of type {@link OAuth2TokenValidator} would be injected into the
 * decoder of the Entra ID path and change its behavior.
 */
public final class TrivoreTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final Set<String> VACO_SCOPES = Set.of("vaco.apiuser", "vaco.admin");

    private static final OAuth2Error NO_VACO_SCOPE = new OAuth2Error("invalid_token", "Token has no VACO scope", null);
    private static final OAuth2Error BAD_AUDIENCE = new OAuth2Error("invalid_token", "Audience does not match the client", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String clientId = token.getSubject();
        List<String> audience = token.getAudience();
        if (clientId == null || audience == null || !audience.contains(clientId)) {
            return OAuth2TokenValidatorResult.failure(BAD_AUDIENCE);
        }
        if (vacoRoles(token).isEmpty()) {
            return OAuth2TokenValidatorResult.failure(NO_VACO_SCOPE);
        }
        return OAuth2TokenValidatorResult.success();
    }

    /**
     * @return the VACO roles that the {@code scope} claim of the token grants, in the order of the claim
     */
    static Set<String> vacoRoles(Jwt token) {
        if (!(token.getClaim("scope") instanceof String scopes)) {
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String scope : scopes.trim().split("\\s+")) {
            if (VACO_SCOPES.contains(scope)) {
                roles.add(scope);
            }
        }
        return roles;
    }
}
