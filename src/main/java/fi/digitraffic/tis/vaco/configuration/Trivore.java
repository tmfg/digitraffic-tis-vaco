package fi.digitraffic.tis.vaco.configuration;

import java.util.List;

/**
 * Trivore ID settings. Without an issuer or without any allowlisted client VACO accepts no Trivore ID tokens.
 *
 * @param issuerUri Issuer of the Trivore ID tokens to accept.
 * @param clients Allowlist of the client credentials clients, each with the roles it is granted.
 */
public record Trivore(String issuerUri, List<TrivoreClient> clients) {

    public Trivore {
        clients = clients == null ? List.of() : List.copyOf(clients);
    }

    public boolean enabled() {
        return issuerUri != null && !issuerUri.isBlank() && !clients.isEmpty();
    }
}
