package fi.digitraffic.tis.vaco.http;

import fi.digitraffic.http.HttpClient;
import fi.digitraffic.http.HttpClientException;
import fi.digitraffic.tis.vaco.http.model.DownloadResponse;
import fi.digitraffic.tis.vaco.http.model.NotificationResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VacoHttpClientTests {

    private VacoHttpClient vacoClient;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpRequest mockRequest;

    @Mock
    private HttpResponse mockResponse;

    @BeforeEach
    void setUp() {
        this.vacoClient = new VacoHttpClient(httpClient);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(httpClient, mockRequest, mockResponse);
    }

    @Test
    void handlesHttpClientExceptionsGracefully() throws IOException, ExecutionException, InterruptedException {
        when(httpClient.headers(any(String[].class))).thenThrow(new HttpClientException("simulated http client error"));

        Path targetFilePath = Files.createTempFile(getClass().getSimpleName(), ".ignored");

        CompletableFuture<DownloadResponse> r = vacoClient.downloadFile(targetFilePath, "https://example.org", null);

        assertThat(r.isDone(), equalTo(true));
        assertThat(r.get().body().isEmpty(), equalTo(true));
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

    private void stubResponse(int stubStatusCode, byte[] stubResponseBody) {
        when(mockResponse.statusCode()).thenReturn(stubStatusCode);
        when(mockResponse.body()).thenReturn(stubResponseBody);
    }
}
