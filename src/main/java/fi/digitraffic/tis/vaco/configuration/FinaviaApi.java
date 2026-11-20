package fi.digitraffic.tis.vaco.configuration;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record FinaviaApi(@NotBlank String apiKey) {
    public static final String URL = "https://apigw.finavia.fi/schedules/all/V1/schedules?format=ssim";

    public Map<String, String> requestHeaders() {
        return Map.of(
            "Cache-Control", "no-cache",
            "app_key", apiKey);
    }

    public static boolean isEntryUrlFinaviaSSIMEndpoint(String entryUrl) {
        return entryUrl != null && URL.equals(entryUrl.trim());
    }
}
