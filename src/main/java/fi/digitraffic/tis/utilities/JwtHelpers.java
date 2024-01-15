package fi.digitraffic.tis.utilities;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

public class JwtHelpers {

    public static Optional<String> safeGet(JwtAuthenticationToken token, String claim) {
        if (token != null && token.getTokenAttributes().containsKey(claim)) {
            return Optional.of(token.getTokenAttributes().get(claim).toString());
        }
        return Optional.empty();
    }
}
