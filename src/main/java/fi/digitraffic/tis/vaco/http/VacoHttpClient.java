package fi.digitraffic.tis.vaco.http;

import fi.digitraffic.http.HttpClient;
import fi.digitraffic.http.HttpClientException;
import fi.digitraffic.tis.vaco.http.model.DownloadResponse;
import fi.digitraffic.tis.vaco.http.model.ImmutableDownloadResponse;
import fi.digitraffic.tis.vaco.http.model.ImmutableNotificationResponse;
import fi.digitraffic.tis.vaco.http.model.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
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

    public CompletableFuture<DownloadResponse> downloadFile(Path targetFilePath,
                                                                      String uri,
                                                                      String etag) {
        logger.info("Downloading file from {} to {} (eTag {})", uri, targetFilePath, etag);

        try {
            Map<String, String> requestHeaders = httpClient.headers(
                "If-None-Match", etag,
                "Accept", "*/*");
            HttpRequest request = httpClient.get(uri, requestHeaders);
            HttpResponse.BodyHandler<Path> bodyHandler = HttpResponse.BodyHandlers.ofFile(targetFilePath);

            return httpClient.send(request, bodyHandler).thenApply(response -> {
                ImmutableDownloadResponse.Builder resp = ImmutableDownloadResponse.builder();
                response.headers().firstValue("ETag").ifPresent(resp::etag);

                logger.info("Response for {} with ETag {} resulted in HTTP status {}", uri, etag, response.statusCode());

                if (response.statusCode() == 304) {
                    return resp.build();
                } else {
                    return resp.body(response.body()).build();
                }
            });
        } catch (HttpClientException e) {
            logger.warn("HTTP execution failure for %s".formatted(uri), e);
            return CompletableFuture.completedFuture(ImmutableDownloadResponse.builder().build());
        }
    }

    public CompletableFuture<NotificationResponse> sendWebhook(String uri, byte[] eventPayload) {
        logger.info("Sending webhook to {}", uri);

        try {
            Map<String, String> requestHeaders = httpClient.headers();
            HttpRequest request = httpClient.post(uri, requestHeaders, eventPayload);
            HttpResponse.BodyHandler<byte[]> bodyHandler = HttpResponse.BodyHandlers.ofByteArray();

            return httpClient.send(request, bodyHandler).thenApply(response -> {
                ImmutableNotificationResponse.Builder resp = ImmutableNotificationResponse.builder();
                resp.response(response.body());

                logger.info("Response for {} resulted in HTTP status {}", uri, response.statusCode());

                return resp.build();
            });
        } catch (HttpClientException e) {
            logger.warn("HTTP execution failure for %s".formatted(uri), e);
            return CompletableFuture.completedFuture(ImmutableNotificationResponse.builder().build());
        }
    }
}
