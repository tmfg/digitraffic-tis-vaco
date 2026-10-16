package fi.digitraffic.tis.vaco.http;

import fi.digitraffic.http.HttpClient;
import fi.digitraffic.http.HttpClientException;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.credentials.CredentialsService;
import fi.digitraffic.tis.vaco.credentials.model.HttpBasicAuthenticationDetails;
import fi.digitraffic.tis.vaco.credentials.model.ImmutableCredentials;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.featureflags.FeatureFlagsService;
import fi.digitraffic.tis.vaco.http.model.DownloadResponse;
import fi.digitraffic.tis.vaco.http.model.NotificationResponse;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VacoHttpClientTests {

    private VacoHttpClient vacoClient;
    @Mock
    private CredentialsService credentialsService;
    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpRequest mockRequest;
    @Mock
    private HttpResponse mockResponse;
    @Mock
    private EntryService entryService;
    @Mock
    private FeatureFlagsService featureFlagsService;
    @Captor
    private ArgumentCaptor<Map<String, String>> headersCaptor;
    private Map<String, String> requestHeaders = new HashMap<>();
    private ImmutableCredentials credentials;
    private ImmutableEntry entry;

    @BeforeEach
    void setUp() {

        this.vacoClient = new VacoHttpClient(httpClient, credentialsService, entryService, featureFlagsService);
        credentials = ImmutableCredentials.copyOf(TestObjects.aCredentials().build());
        entry = ImmutableEntry.copyOf(TestObjects.anEntry().build());

    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(httpClient, mockRequest, mockResponse, entryService, featureFlagsService);
    }

    @Test
    void handlesHttpClientExceptionsGracefully() throws IOException, ExecutionException, InterruptedException {
        given(featureFlagsService.isFeatureFlagEnabled("tasks.prepareDownload.skipDownloadOnStaleETag")).willReturn(true);
        when(httpClient.get(any(String.class),any(Map.class))).thenThrow(new HttpClientException("simulated http client error"));

        Path targetFilePath = Files.createTempFile(getClass().getSimpleName(), ".ignored");

        CompletableFuture<DownloadResponse> r = vacoClient.downloadFile(targetFilePath, "https://example.org", entry);

        assertThat(r.isDone(), equalTo(true));
        assertThat(r.get().body().isEmpty(), equalTo(true));
    }

    @Test
    void addsCacheRevalidationHeadersToDownloads() throws IOException {
        given(featureFlagsService.isFeatureFlagEnabled("tasks.prepareDownload.skipDownloadOnStaleETag")).willReturn(false);
        when(httpClient.get(any(String.class), headersCaptor.capture())).thenThrow(new HttpClientException("simulated http client error"));

        Path targetFilePath = Files.createTempFile(getClass().getSimpleName(), ".ignored");

        vacoClient.downloadFile(targetFilePath, "https://example.org", entry);

        assertThat(headersCaptor.getValue().get("Cache-Control"), equalTo("no-cache"));
        assertThat(headersCaptor.getValue().get("Pragma"), equalTo("no-cache"));
    }

    @Test
    void downloadFileWithoutEntryWritesResponseBodyAndReturnsOk() throws IOException, ExecutionException, InterruptedException {
        // targetFilePath must not already exist, matching how real callers (e.g. TempFiles.getTaskTempFile)
        // construct it: Files.copy(InputStream, Path) throws FileAlreadyExistsException otherwise.
        Path targetFilePath = Files.createTempDirectory(getClass().getSimpleName()).resolve("downloaded.ignored");
        byte[] stubResponseBody = "stops and quays payload".getBytes(StandardCharsets.UTF_8);
        InputStream spyBody = spy(new ByteArrayInputStream(stubResponseBody));

        when(httpClient.get(eq("https://example.org"), headersCaptor.capture())).thenReturn(mockRequest);
        when(httpClient.send(eq(mockRequest), any(HttpResponse.BodyHandler.class))).thenReturn(CompletableFuture.completedFuture(mockResponse));
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(spyBody);
        when(mockResponse.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));

        CompletableFuture<DownloadResponse> r = vacoClient.downloadFile(targetFilePath, "https://example.org");

        assertThat(r.isDone(), equalTo(true));
        assertThat(r.get().result(), equalTo(DownloadResponse.Result.OK));
        assertThat(Files.readAllBytes(targetFilePath), equalTo(stubResponseBody));
        assertThat(headersCaptor.getValue().containsKey("If-None-Match"), equalTo(false));
        assertThat(headersCaptor.getValue().containsKey("Authorization"), equalTo(false));
        verify(spyBody).close();
    }

    @Test
    void downloadFileWithoutEntryClosesResponseBodyOnNotModified() throws IOException, ExecutionException, InterruptedException {
        Path targetFilePath = Files.createTempDirectory(getClass().getSimpleName()).resolve("downloaded.ignored");
        InputStream spyBody = spy(new ByteArrayInputStream(new byte[0]));

        when(httpClient.get(eq("https://example.org"), headersCaptor.capture())).thenReturn(mockRequest);
        when(httpClient.send(eq(mockRequest), any(HttpResponse.BodyHandler.class))).thenReturn(CompletableFuture.completedFuture(mockResponse));
        when(mockResponse.statusCode()).thenReturn(304);
        when(mockResponse.body()).thenReturn(spyBody);
        when(mockResponse.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));

        CompletableFuture<DownloadResponse> r = vacoClient.downloadFile(targetFilePath, "https://example.org");

        assertThat(r.isDone(), equalTo(true));
        assertThat(r.get().result(), equalTo(DownloadResponse.Result.NOT_MODIFIED));
        verify(spyBody).close();
    }

    @Test
    void copyWithSizeLimitThrowsAndDoesNotWriteFullStreamWhenExceedingCap() throws IOException {
        byte[] data = new byte[20];
        long cap = 10L;
        Path target = Files.createTempDirectory(getClass().getSimpleName()).resolve("capped.ignored");

        assertThrows(IOException.class, () -> VacoHttpClient.copyWithSizeLimit(new ByteArrayInputStream(data), target, cap));

        if (Files.exists(target)) {
            assertTrue(Files.size(target) <= cap);
        }
    }

    @Test
    void copyWithSizeLimitCopiesStreamWhenUnderCap() throws IOException {
        byte[] data = "small payload".getBytes(StandardCharsets.UTF_8);
        Path target = Files.createTempDirectory(getClass().getSimpleName()).resolve("capped.ignored");

        VacoHttpClient.copyWithSizeLimit(new ByteArrayInputStream(data), target, 1024L);

        assertThat(Files.readAllBytes(target), equalTo(data));
    }

    @Test
    void downloadFileWithoutEntryHandlesHttpClientExceptionsGracefully() throws IOException, ExecutionException, InterruptedException {
        when(httpClient.get(any(String.class), any(Map.class))).thenThrow(new HttpClientException("simulated http client error"));

        Path targetFilePath = Files.createTempFile(getClass().getSimpleName(), ".ignored");

        CompletableFuture<DownloadResponse> r = vacoClient.downloadFile(targetFilePath, "https://example.org");

        assertThat(r.isDone(), equalTo(true));
        assertThat(r.get().result(), equalTo(DownloadResponse.Result.FAILED_DOWNLOAD));
    }

    @Test
    void webhook() throws ExecutionException, InterruptedException {
        Map<String, String> emptyHeaders = Map.of();
        when(httpClient.headers(any(String[].class))).thenReturn(emptyHeaders);
        when(httpClient.post(any(String.class), eq(emptyHeaders), any(byte[].class))).thenReturn(mockRequest);
        when(httpClient.send(eq(mockRequest), any(HttpResponse.BodyHandler.class))).thenReturn(CompletableFuture.completedFuture(mockResponse));

        byte[] stubResponseBody = "fake response for webhook".getBytes(StandardCharsets.UTF_8);
        stubResponse(200, stubResponseBody);

        CompletableFuture<NotificationResponse> r = vacoClient.sendWebhook("http://example.fi/webhook/abcdef123456", "hello".getBytes(StandardCharsets.UTF_8));

        assertThat(r.isDone(), equalTo(true));
        assertThat(r.get().response().get(), equalTo(stubResponseBody));
    }

    @Test
    void testAuthorizationHeadersWithCredentials() {

        given(credentialsService.findByPublicId(credentials.publicId())).willReturn(Optional.of(credentials));

        requestHeaders = vacoClient.addAuthorizationHeader(credentials.publicId());

        HttpBasicAuthenticationDetails details = (HttpBasicAuthenticationDetails) credentials.details();
        String userId = details.userId();
        String password = details.password();

        assertTrue(requestHeaders.containsKey("Authorization"));
        String authHeader = requestHeaders.get("Authorization");
        String expectedAuthValue = "Basic " + Base64.getEncoder().encodeToString((userId + ":" + password).getBytes());
        assertEquals(expectedAuthValue, authHeader);
    }

    @Test
    void testAuthorizationHeadersWithoutCredentials() {

        given(credentialsService.findByPublicId(null)).willReturn(Optional.empty());

        requestHeaders = vacoClient.addAuthorizationHeader(null);
        assertTrue(requestHeaders.isEmpty());

    }

    private void stubResponse(int stubStatusCode, byte[] stubResponseBody) {
        when(mockResponse.statusCode()).thenReturn(stubStatusCode);
        when(mockResponse.body()).thenReturn(stubResponseBody);
    }

}
