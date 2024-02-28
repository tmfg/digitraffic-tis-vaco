package fi.digitraffic.tis.http;

import com.github.mizosoft.methanol.Methanol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class HttpClient {

    private static final String USER_AGENT =  "DigiTraffic TIS/r2024-01";
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public final Methanol methanol;

    public HttpClient() {
        this.methanol = Methanol
            .newBuilder()
            //.cache(...)
            //.interceptor(...)
            .userAgent(USER_AGENT)
            .connectTimeout(Duration.ofSeconds(15))
            .requestTimeout(Duration.ofSeconds(15))
            .headersTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(5))
            .build();
    }

    public CompletableFuture<Optional<Path>> downloadFile(Path targetFilePath,
                                                          String url,
                                                          String etag) {
        logger.info("Downloading file from {} to {} (eTag {})", url, targetFilePath, etag);

        Map<String, String> requestHeaders = headers(
            "If-None-Match", etag,
            "Accept", "application/zip, */*");

        HttpRequest request = buildGetRequest(url, requestHeaders);
        HttpResponse.BodyHandler<Path> bodyHandler = HttpResponse.BodyHandlers.ofFile(targetFilePath);
        return methanol.sendAsync(request, bodyHandler).thenApply(response -> {
            logger.info("Response for {} with ETag {} resulted in HTTP status {}", url, etag, response.statusCode());
            if (response.statusCode() == 304) {
                return Optional.empty();
            } else {
                return Optional.of(response.body());
            }
        });
    }

    protected Map<String, String> headers(String... headersAndValues) {
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

    private HttpRequest buildGetRequest(String url, Map<String, String> headers) {
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

