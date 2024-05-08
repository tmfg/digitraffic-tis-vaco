package fi.digitraffic.http;

import com.github.mizosoft.methanol.Methanol;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Base HTTP client operations.
 */
public class HttpClient {

    private static final String BASE_USER_AGENT =  "DigiTraffic TIS/r2024-01";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final HttpClientConfiguration configuration;

    public final Methanol methanol;

    public HttpClient(HttpClientConfiguration configuration) {
        this.configuration = configuration;
        this.methanol = createMethanol(configuration);
    }

    private static Methanol createMethanol(HttpClientConfiguration configuration) {
        Methanol.Builder builder = Methanol
            .newBuilder()
            //.cache(...)
            //.interceptor(...)
            .connectTimeout(configuration.connectionTimeout())
            .requestTimeout(configuration.requestTimeout())
            .headersTimeout(configuration.headersTimeout())
            .readTimeout(configuration.readTimeout())
            .followRedirects(Redirect.ALWAYS);

        configuration.userAgentExtension()
            .map(ext -> BASE_USER_AGENT + " " + ext)
            .or(() -> Optional.of(BASE_USER_AGENT))
            .ifPresent(builder::userAgent);

        configuration.baseUri().ifPresent(builder::baseUri);

        return builder.build();
    }

    public <T> CompletableFuture<HttpResponse<T>> send(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) {
        return methanol.sendAsync(request, bodyHandler);
    }

    public Map<String, String> headers(String... headersAndValues) {
        if (headersAndValues.length % 2 != 0) {
            throw new HttpClientException("Tried to generate request headers with uneven number of key-value pairs! Provided values must be divisible by two, " + headersAndValues.length + " values provided instead");
        }
        Map<String, String> headers = new HashMap<>();
        for (int i = 0; i < headersAndValues.length;) {
            String key = headersAndValues[i++];
            String value = headersAndValues[i++];
            if (Objects.nonNull(key) && Objects.nonNull(value)) {
                headers.put(key, value);
            }
        }
        return headers;
    }

    public HttpRequest get(String uri, Map<String, String> headers) {
        try {
            var builder = HttpRequest.newBuilder()
                .GET()
                .uri(toUri(uri));

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder = builder.header(entry.getKey(), entry.getValue());
            }

            return builder.build();
        } catch (URISyntaxException e) {
            throw new HttpClientException("Provided URI %s is invalid".formatted(uri), e);
        } catch (IllegalArgumentException e) {
            throw new HttpClientException("Could not construct HTTP GET request for URI %s".formatted(uri), e);
        }
    }

    @VisibleForTesting
    protected URI toUri(String uri) throws URISyntaxException {
        // XXX: It's 2024 and Java still doesn't have a native URI builder, and options are all frameworky
        URI uriObj = new URI(uri);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(uri);
        if (uriObj.getScheme() == null) {
            builder.scheme(configuration.defaultScheme());
        }
        // Treat provided URIs as pre-encoded. See tests for more details.
        return builder.build(true).toUri();
    }
}

