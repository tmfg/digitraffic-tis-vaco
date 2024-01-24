package fi.digitraffic.tis.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class HttpClient {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public final java.net.http.HttpClient javaHttpClient;

    public HttpClient() {
        this.javaHttpClient = java.net.http.HttpClient.newBuilder().build();
    }

    public CompletableFuture<Optional<Path>> downloadFile(Path targetFilePath,
                                                          String url,
                                                          String etag) {
        logger.info("Downloading file from {} to {} (eTag {})", url, targetFilePath, etag);
        Map<String, String> headers = Map.of(
            "If-None-Match", etag,
            "Accept-Encoding", "gzip"
        );
        HttpRequest request = buildGetRequest(url, headers);
        HttpResponse.BodyHandler<Path> bodyHandler = HttpResponse.BodyHandlers.ofFile(targetFilePath);
        return javaHttpClient.sendAsync(request, bodyHandler).thenApply(response -> {
            if (response.statusCode() == 304) {
                return Optional.empty();
            } else {
                return Optional.of(response.body());
            }
        });
    }

    private HttpRequest buildGetRequest(String url, Map<String, String> headers) {
        try {
            var builder = HttpRequest.newBuilder()
                .GET()
                .uri(new URI(url))
                .headers("User-Agent", "DigiTraffic TIS / 0.1");

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder = builder.header(entry.getKey(), entry.getValue());
            }

            return builder.build();
        } catch (URISyntaxException e) {
            throw new HttpClientException(String.format("Provided URI %s is invalid", url), e);
        }
    }
}

