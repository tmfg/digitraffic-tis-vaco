package fi.digitraffic.tis.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class HttpClient {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public final java.net.http.HttpClient javaHttpClient;

    public HttpClient() {
        this.javaHttpClient = java.net.http.HttpClient.newBuilder().build();
    }

    public CompletableFuture<HttpResponse<Path>> downloadFile(Path targetFilePath,
                                                              String url,
                                                              String etag) {
        logger.info("Downloading file to {} from {} (eTag {})", targetFilePath, url, etag);
        HttpRequest request = buildGetRequest(url, etag);
        HttpResponse.BodyHandler<Path> bodyHandler = HttpResponse.BodyHandlers.ofFile(targetFilePath);
        return javaHttpClient.sendAsync(request, bodyHandler);
    }

    private HttpRequest buildGetRequest(String url, String etag) {
        try {
            var builder = HttpRequest.newBuilder()
                .header("User-Agent", "DigiTraffic TIS / 0.1")
                .GET()
                .uri(new URI(url));

            if (etag != null) {
                builder = builder.header("ETag", etag);
            }

            return builder.build();
        } catch (URISyntaxException e) {
            throw new HttpClientException(String.format("Provided URI %s is invalid", url), e);
        }
    }
}

