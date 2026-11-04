package fi.digitraffic.tis.vaco.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Set;

/**
 * Accepts a Trivore ID token only when its client is on the allowlist and the token was issued for that same client.
 * <p>
 * This is deliberately not a Spring bean. A bean of type {@link OAuth2TokenValidator} would be injected into the
 * decoder of the Entra ID path and change its behavior.
 */
public final class TrivoreTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error NOT_ALLOWED = new OAuth2Error("invalid_token", "Client is not allowed", null);
    private static final OAuth2Error BAD_AUDIENCE = new OAuth2Error("invalid_token", "Audience does not match the client", null);

    private final Set<String> allowedClientIds;

    public TrivoreTokenValidator(Set<String> allowedClientIds) {
        this.allowedClientIds = Set.copyOf(allowedClientIds);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String clientId = token.getSubject();
        if (clientId == null || !allowedClientIds.contains(clientId)) {
            return OAuth2TokenValidatorResult.failure(NOT_ALLOWED);
        }
        List<String> audience = token.getAudience();
        if (audience == null || !audience.contains(clientId)) {
            return OAuth2TokenValidatorResult.failure(BAD_AUDIENCE);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
