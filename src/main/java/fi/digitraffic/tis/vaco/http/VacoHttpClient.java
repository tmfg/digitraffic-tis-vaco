package fi.digitraffic.tis.vaco.http;

import com.google.common.annotations.VisibleForTesting;
import fi.digitraffic.http.HttpClient;
import fi.digitraffic.http.HttpClientException;
import fi.digitraffic.tis.vaco.credentials.CredentialsService;
import fi.digitraffic.tis.vaco.credentials.model.Credentials;
import fi.digitraffic.tis.vaco.credentials.model.HttpBasicAuthenticationDetails;
import fi.digitraffic.tis.vaco.db.model.CredentialsRecord;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.http.model.DownloadResponse;
import fi.digitraffic.tis.vaco.http.model.ImmutableDownloadResponse;
import fi.digitraffic.tis.vaco.http.model.ImmutableNotificationResponse;
import fi.digitraffic.tis.vaco.http.model.NotificationResponse;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.RuleExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * VACO service's internal HTTP client with service specific specializations.
 */
public class VacoHttpClient {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final HttpClient httpClient;
    private final CredentialsService credentialsService;
    private final EntryService entryService;

    public VacoHttpClient(HttpClient httpClient, CredentialsService credentialsService, EntryService entryService) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.credentialsService = Objects.requireNonNull(credentialsService);
        this.entryService = Objects.requireNonNull(entryService);
    }

    public CompletableFuture<DownloadResponse> downloadFile(Path targetFilePath,
                                                            String uri,
                                                            Entry entry) {
        logger.info("Downloading file from {} to {} (eTag {})", uri, targetFilePath, entry.etag());

        try {

            Map<String, String> requestHeaders = new HashMap<>();

            requestHeaders.put("Accept", "*/*");

            if (entry.etag() != null && !entry.etag().isEmpty()) {
                requestHeaders.put("If-None-Match", entry.etag());
            }

            if (entry.credentials() != null) {
                requestHeaders.putAll(addAuthorizationHeader(entry.credentials()));
            } else {
                requestHeaders.putAll(addAuthorizationHeaderAutomatically(entry.businessId(), uri, entry));
            }

            HttpRequest request = httpClient.get(uri, requestHeaders);
            HttpResponse.BodyHandler<InputStream> bodyHandler = HttpResponse.BodyHandlers.ofInputStream();

            return httpClient.send(request, bodyHandler).thenApply(response -> {
                ImmutableDownloadResponse.Builder resp = ImmutableDownloadResponse.builder();
                response.headers().firstValue("ETag").ifPresent(resp::etag);

                logger.info("Response for {} with ETag {} resulted in HTTP status {}", uri, entry.etag(), response.statusCode());

                if (response.statusCode() == 304) {
                    return resp.build();
                } else {
                    try {
                        Files.copy(response.body(), targetFilePath);
                    } catch (IOException e) {
                        throw new RuleExecutionException("Failed to write download stream of " + entry.publicId() + "/" + uri + " into file " + targetFilePath, e);
                    }
                    return resp.body(targetFilePath).build();
                }
            });
        } catch (HttpClientException e) {
            logger.warn("HTTP execution failure for %s".formatted(uri), e);
            return CompletableFuture.completedFuture(ImmutableDownloadResponse.builder().build());
        }
    }

    @VisibleForTesting
    protected Map<String, String> addAuthorizationHeader(String credentials) {

        Map<String,String> extraHeaders = new HashMap<>();

        Optional<Credentials> entryCredentials = credentialsService.findByPublicId(credentials);

        entryCredentials.ifPresent(c -> {
            extraHeaders.putAll(setHeaders(c));
        });

        return extraHeaders;
    }

    @VisibleForTesting
    public Map<String, String> addAuthorizationHeaderAutomatically(String businessId, String uri, Entry entry) {

        Map<String,String> extraHeaders = new HashMap<>();

        Optional<Credentials> matchingCredentials = credentialsService.findMatchingCredentials(businessId, uri);

        if (matchingCredentials.isPresent()) {
            Optional<CredentialsRecord> credentialsRecord = credentialsService.findCredentialsRecordByPublicId(matchingCredentials.get().publicId());

            if (credentialsRecord.isPresent()){
                boolean entryUpdated = entryService.updateCredentials(entry, credentialsRecord.get().id());
                if (entryUpdated) {
                    logger.info("Entry {} updated to contain credentials", entry.publicId());
                }
            }
            logger.info("Credentials {} set to entry {} based on url pattern", matchingCredentials.get().publicId(), entry.publicId());
            extraHeaders.putAll(setHeaders(matchingCredentials.get()));
        }
        return  extraHeaders;

    }

    private Map<String, String> setHeaders(Credentials credentials) {

        Map<String,String> extraHeaders = new HashMap<>();

        switch (credentials.details()) {
            case HttpBasicAuthenticationDetails httpBasic -> {
                String auth = httpBasic.userId() + ":" + httpBasic.password();
                extraHeaders.put("Authorization", "Basic " + Base64.getEncoder().encodeToString(auth.getBytes()));
                return extraHeaders;
            }
            case null -> logger.warn("Credentials {} don't contain authentication details ", credentials.publicId());
            default -> logger.warn("Unhandled credentials sub type {}", credentials.details().getClass());
        }
        return extraHeaders;
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
            logger.warn("HTTP execution failure for {}", uri, e);
            return CompletableFuture.completedFuture(ImmutableNotificationResponse.builder().build());
        }
    }
}
