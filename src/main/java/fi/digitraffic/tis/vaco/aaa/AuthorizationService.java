package fi.digitraffic.tis.vaco.aaa;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthorizationService {


    /**
     * Use this method to gain access to current user's JWT token. Remember to handle missing tokens in case the user
     * hasn't authenticated!
     *
     * There are other ways of accessing the token, e.g. through controller handler parameter injection, but doing so
     * blocks link generation. Also considering the current state of Spring Security's on-going migration/refactoring
     * and the amount of legacy documentation online it is really hard to quantify what is the least insane approach for
     * accessing the token itself.
     *
     * For the reasons above, this service exists mainly to isolate the insanity, not for providing true authorization
     * related actions. Lean on Spring Security where available.
     *
     * @return Current user's {@link JwtAuthenticationToken}
     */
    public Optional<JwtAuthenticationToken> currentToken() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
            .filter(JwtAuthenticationToken.class::isInstance)
            .map(JwtAuthenticationToken.class::cast);
    }
}
