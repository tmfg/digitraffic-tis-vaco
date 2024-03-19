package fi.digitraffic.tis.vaco.http;

import fi.digitraffic.http.HttpClient;
import fi.digitraffic.http.HttpClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * VACO service's internal HTTP client with service specific specializations.
 */
public class VacoHttpClient {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final HttpClient httpClient;

    public VacoHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public CompletableFuture<Optional<Path>> downloadFile(Path targetFilePath,
                                                          String uri,
                                                          String etag) {
        logger.info("Downloading file from {} to {} (eTag {})", uri, targetFilePath, etag);

        try {
            Map<String, String> requestHeaders = httpClient.headers(
                "If-None-Match", etag,
                "Accept", "application/zip, */*");
            HttpRequest request = httpClient.get(uri, requestHeaders);
            HttpResponse.BodyHandler<Path> bodyHandler = HttpResponse.BodyHandlers.ofFile(targetFilePath);

            return httpClient.send(request, bodyHandler).thenApply(response -> {
                logger.info("Response for {} with ETag {} resulted in HTTP status {}", uri, etag, response.statusCode());
                if (response.statusCode() == 304) {
                    return Optional.empty();
                } else {
                    return Optional.of(response.body());
                }
            });
        } catch (HttpClientException e) {
            logger.warn("HTTP execution failure for %s".formatted(uri), e);
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }
}
