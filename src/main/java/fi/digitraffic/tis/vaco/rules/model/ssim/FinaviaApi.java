package fi.digitraffic.tis.vaco.rules.model.ssim;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class FinaviaApi {
    public static final String URL = "https://apigw.finavia.fi/schedules/all/V1/schedules?format=ssim";
    private final Map<String, String> requestHeaders = new HashMap<>();

    public FinaviaApi(@Value("${vaco.finavia.api-key}") String finaviaApiKey) {
        requestHeaders.put("Cache-Control", "no-cache");
        requestHeaders.put("app_key", finaviaApiKey);
    }

    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }

    public static boolean isEntryUrlFinaviaSSIMEndpoint(String entryUrl) {
        return entryUrl != null && URL.equals(entryUrl.trim());
    }
}
