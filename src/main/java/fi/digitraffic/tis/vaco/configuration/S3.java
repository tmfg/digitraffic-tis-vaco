package fi.digitraffic.tis.vaco.configuration;

/**
 * @param endpoint Localstack has custom endpoint for S3 which is why this is here.
 */
public record S3(String endpoint) {
}
