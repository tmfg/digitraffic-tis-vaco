package fi.digitraffic.tis.vaco.webhooks;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.JsonNode;
import fi.digitraffic.tis.utilities.Responses;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.api.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Generic webhook receiving API endpoint.
 */
@RestController
@RequestMapping({"/v1/webhooks"})
@Profile("!local & !tests")
public class WebhooksController {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Map<String, String> secrets;
    private final TrivoreWebhooksService trivoreWebhooksService;

    public WebhooksController(@Value("#{${vaco.webhooks.shared-secrets}}") Map<String, String> sharedSecrets,
                              TrivoreWebhooksService trivoreWebhooksService) {
        this.secrets = invertMap(sharedSecrets);
        this.trivoreWebhooksService = Objects.requireNonNull(trivoreWebhooksService);
    }

    private Map<String, String> invertMap(Map<String, String> m) {
        Map<String, String> inverted = new HashMap<>();
        for (Map.Entry<String, String> entry : m.entrySet()) {
            inverted.put(entry.getValue(), entry.getKey());
        }
        return inverted;
    }


    @PostMapping(path = "")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<Resource<String>> receiveWebhook(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
        @RequestBody JsonNode rawWebhookData) {

        return switch (secrets.get(authorization)) {
            case "tis-peti" -> handlePetiWebhook(rawWebhookData);
            default -> {
                logger.warn("Unknown Webhook caller: {} / {}", authorization, rawWebhookData);
                yield Responses.unauthorized("unknown Webhook caller");
            }
        };
    }

    private ResponseEntity<Resource<String>> handlePetiWebhook(JsonNode webhookData) {
        trivoreWebhooksService.handleWebhook(webhookData);
        return Responses.ok("accepted");
    }

}
