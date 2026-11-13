package fi.digitraffic.tis.vaco.ui;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

class SourceUrlsTests {

    private static final String FEED_URL = "https://example.fi/feed.zip";
    private static final String FEED_URL_WITH_TOKEN = FEED_URL + "?token=secret";

    @Test
    void removesQueryString() {
        assertThat(SourceUrls.forVisitor(FEED_URL + "?token=secret&x=1"), equalTo(FEED_URL));
    }

    @Test
    void removesFragment() {
        assertThat(SourceUrls.forVisitor(FEED_URL + "#part"), equalTo(FEED_URL));
    }

    @Test
    void removesUserInfo() {
        assertThat(SourceUrls.forVisitor("https://user:pass@example.fi/feed.zip"), equalTo(FEED_URL));
    }

    @Test
    void removesAllThreeAndKeepsPortAndEncodedPath() {
        assertThat(
            SourceUrls.forVisitor("https://user:pass@example.fi:8443/a%20b/feed.zip?token=secret#part"),
            equalTo("https://example.fi:8443/a%20b/feed.zip"));
    }

    @Test
    void keepsUrlWithoutQueryFragmentOrUserInfoUnchanged() {
        assertThat(SourceUrls.forVisitor(FEED_URL), equalTo(FEED_URL));
        assertThat(SourceUrls.forVisitor("https://example.fi"), equalTo("https://example.fi"));
    }

    @Test
    void keepsNoDataMarkerUnchanged() {
        assertThat(SourceUrls.forVisitor("NO_DATA"), equalTo("NO_DATA"));
    }

    @Test
    void keepsNull() {
        assertThat(SourceUrls.forVisitor(null), nullValue());
    }

    @Test
    void unparsableUrlGivesEmptyValue() {
        assertThat(SourceUrls.forVisitor("https://u:p@host/a b?token=x"), equalTo(""));
    }

    @Test
    void opaqueUrlGivesEmptyValue() {
        assertThat(SourceUrls.forVisitor("mailto:someone@example.fi?subject=x"), equalTo(""));
    }

    @Test
    void masksEveryOccurrenceInText() {
        assertThat(
            SourceUrls.maskInText("Failed to read " + FEED_URL_WITH_TOKEN + ", retrying", FEED_URL_WITH_TOKEN),
            equalTo("Failed to read " + FEED_URL + ", retrying"));
    }

    @Test
    void textWithoutTheUrlStaysUnchanged() {
        assertThat(SourceUrls.maskInText("Some message", FEED_URL_WITH_TOKEN), equalTo("Some message"));
    }

    @Test
    void textMaskingToleratesNullInputs() {
        assertThat(SourceUrls.maskInText(null, "https://example.fi/?a=b"), nullValue());
        assertThat(SourceUrls.maskInText("text", null), equalTo("text"));
    }
}
