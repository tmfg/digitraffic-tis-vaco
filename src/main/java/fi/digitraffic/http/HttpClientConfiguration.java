package fi.digitraffic.http;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.time.Duration;
import java.util.Optional;

@Value.Immutable
@JsonSerialize(as = ImmutableHttpClientConfiguration.class)
@JsonDeserialize(as = ImmutableHttpClientConfiguration.class)
public interface HttpClientConfiguration {

    /**
     * Default URI scheme to use when one isn't supplied by caller. Can be overridden.
     */
    String DEFAULT_SCHEME = "https";

    /**
     * Provide extension identifier for user agent. Recommended string format is <code>extensionname/extensionversion</code>,
     * for example <code>VACO/3e712ea</code>
     * @return User agent extension if provided, empty otherwise.
     */
    Optional<String> userAgentExtension();

    @Value.Default
    default Duration connectionTimeout() {
        return Duration.ofSeconds(15);
    }

    @Value.Default
    default Duration requestTimeout() {
        return Duration.ofSeconds(15);
    }

    @Value.Default
    default Duration headersTimeout() {
        return Duration.ofSeconds(5);
    }

    @Value.Default
    default Duration readTimeout() {
        return Duration.ofSeconds(5);
    }

    Optional<String> baseUri();

    @Value.Default
    default String defaultScheme() {
        return DEFAULT_SCHEME;
    }
}
