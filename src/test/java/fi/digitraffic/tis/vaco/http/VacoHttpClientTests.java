package fi.digitraffic.tis.vaco.http;

import fi.digitraffic.http.HttpClient;
import fi.digitraffic.http.HttpClientException;
import fi.digitraffic.tis.vaco.http.model.DownloadResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VacoHttpClientTests {

    @Mock
    private HttpClient httpClient;

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void handlesHttpClientExceptionsGracefully() throws IOException, ExecutionException, InterruptedException {
        VacoHttpClient client = new VacoHttpClient(httpClient);

        when(httpClient.headers(any(String[].class))).thenThrow(new HttpClientException("simulated http client error"));

        Path targetFilePath = Files.createTempFile(getClass().getSimpleName(), ".ignored");

        CompletableFuture<DownloadResponse> r = client.downloadFile(targetFilePath, "https://example.org", null);

        assertThat(r.isDone(), equalTo(true));
        assertThat(r.get().body().isEmpty(), equalTo(true));
    }
}
