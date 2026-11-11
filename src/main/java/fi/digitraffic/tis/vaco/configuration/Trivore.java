package fi.digitraffic.tis.vaco.configuration;

/**
 * Trivore ID settings. Without an issuer VACO accepts no Trivore ID tokens.
 *
 * @param issuerUri Issuer of the Trivore ID tokens to accept.
 */
public record Trivore(String issuerUri) {

    public boolean enabled() {
        return issuerUri != null && !issuerUri.isBlank();
    }
}
