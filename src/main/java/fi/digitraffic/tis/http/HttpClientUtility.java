package fi.digitraffic.tis.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class HttpClientUtility {

    public final HttpClient httpClient;

    public HttpClientUtility() {
        this.httpClient = HttpClient.newBuilder().build();
    }

    public CompletableFuture<HttpResponse<Path>> downloadFile(Path filePath, String url, String etag) {
        HttpRequest request = buildGetRequest(url, etag);
        HttpResponse.BodyHandler<Path> bodyHandler = HttpResponse.BodyHandlers.ofFile(filePath);
        return httpClient.sendAsync(request, bodyHandler);
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
            throw new HttpClientUtilityException(String.format("Provided URI %s is invalid", url), e);
        }
    }
}

class HttpClientUtilityException extends RuntimeException {
    public HttpClientUtilityException(String message) {
        super(message);
    }

    public HttpClientUtilityException(String message, Throwable cause) {
        super(message, cause);
    }
}