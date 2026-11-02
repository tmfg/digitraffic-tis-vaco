package fi.digitraffic.tis.vaco.configuration;

import jakarta.validation.constraints.NotBlank;

import java.util.Set;

/**
 * One allowlisted Trivore ID client.
 *
 * @param clientId Client id, which is the {@code sub} claim of its tokens.
 * @param roles Roles granted to the client.
 */
public record TrivoreClient(@NotBlank String clientId, Set<String> roles) {

    public TrivoreClient {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
