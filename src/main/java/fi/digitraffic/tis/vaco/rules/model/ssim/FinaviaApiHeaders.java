package fi.digitraffic.tis.vaco.rules.model.ssim;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class FinaviaApiHeaders {
    private final Map<String, String> requestHeaders = new HashMap<>();

    public FinaviaApiHeaders(@Value("${vaco.finavia.api-key}") String finaviaApiKey) {
        requestHeaders.put("Cache-Control", "no-cache");
        requestHeaders.put("app_key", finaviaApiKey);
    }

    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }
}
