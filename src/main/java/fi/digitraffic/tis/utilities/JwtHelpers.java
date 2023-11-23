package fi.digitraffic.tis.utilities;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

public class JwtHelpers {

    public static Optional<String> safeGet(JwtAuthenticationToken token, String companyNameClaim) {
        if (token != null && token.getTokenAttributes().containsKey(companyNameClaim)) {
            return Optional.of(token.getTokenAttributes().get(companyNameClaim).toString());
        }
        return Optional.empty();
    }
}
