package fi.digitraffic.http;

import com.github.mizosoft.methanol.Methanol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
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

    public final Methanol methanol;

    public HttpClient(HttpClientConfiguration configuration) {
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
            .readTimeout(configuration.readTimeout());

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

    public HttpRequest get(String url, Map<String, String> headers) {
        try {
            var builder = HttpRequest.newBuilder()
                .GET()
                .uri(new URI(url));

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder = builder.header(entry.getKey(), entry.getValue());
            }

            return builder.build();
        } catch (URISyntaxException e) {
            throw new HttpClientException(String.format("Provided URI %s is invalid", url), e);
        }
    }
}

