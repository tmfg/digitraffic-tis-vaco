package fi.digitraffic.tis.vaco.configuration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinaviaApiTests {

    @Test
    void providesRequiredHeadersWithConfiguredApiKey() {
        FinaviaApi finavia = new FinaviaApi("test-api-key");

        assertEquals(Map.of(
            "Cache-Control", "no-cache",
            "app_key", "test-api-key"
        ), finavia.requestHeaders());
    }

    @Test
    void recognizesFinaviaSsimEndpointAndTrimsSurroundingWhitespace() {
        assertTrue(FinaviaApi.isEntryUrlFinaviaSSIMEndpoint(FinaviaApi.URL));
        assertTrue(FinaviaApi.isEntryUrlFinaviaSSIMEndpoint("  " + FinaviaApi.URL + "  "));
    }

    @Test
    void rejectsNullOrDifferentEndpoints() {
        assertFalse(FinaviaApi.isEntryUrlFinaviaSSIMEndpoint(null));
        assertFalse(FinaviaApi.isEntryUrlFinaviaSSIMEndpoint("https://example.com/schedules"));
        assertFalse(FinaviaApi.isEntryUrlFinaviaSSIMEndpoint(FinaviaApi.URL + "&other=value"));
    }
}
