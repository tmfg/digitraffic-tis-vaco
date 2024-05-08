package fi.digitraffic.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
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

    @Test
    void usesProvidedDefaultUriSchemeForSchemelessUris() throws URISyntaxException {
        URI uri = httpClient.toUri("exaple.org");
        assertThat(uri.getScheme(), equalTo(HttpClientConfiguration.DEFAULT_SCHEME));
    }

    /**
     * Some URLs, such as S3 presigned URLs, contain pre-encoded parameters which should be used verbatim
     * Java URI however ignores such and performs another unnecessary decoding effectively corrupting URLs
     * this test ensures this doesn't happen.
     */
    @Test
    void doesntDoubleEncodeUrisWithPredefinedParameters() throws URISyntaxException {
        String original = "https://example.org/keep%2f/?foo=%2fthis-is-on-purpose";
        URI uri = httpClient.toUri(original);
        assertThat(uri.toString(), equalTo(original));
    }
}
