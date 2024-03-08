package fi.digitraffic.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpClientTests {

    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        httpClient = new HttpClient(ImmutableHttpClientConfiguration.builder().build());
    }

    @Test
    void canCreateHeadersFromPossiblyNullValues() {

        assertThat(httpClient.headers(), equalTo(Map.of()));
        assertThat(httpClient.headers("foo", null), equalTo(Map.of()));
        assertThat(httpClient.headers("foo", "bar"), equalTo(Map.of("foo", "bar")));
        assertThat(httpClient.headers("foo", "bar", "quu", null), equalTo(Map.of("foo", "bar")));
        assertThat(httpClient.headers("foo", "bar", "quu", "qux"), equalTo(Map.of("foo", "bar", "quu", "qux")));
    }

    @Test
    void throwsExceptionOnUnevenNumberOfHeaderValues() {
        assertThrows(HttpClientException.class, () -> httpClient.headers("moi"), "Uneven number of values should not be allowed");
    }
}
