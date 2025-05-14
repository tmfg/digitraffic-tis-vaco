package fi.digitraffic.tis.vaco.webhooks;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.JsonNode;
import fi.digitraffic.tis.utilities.Responses;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Generic webhook receiving API endpoint.
 */
@RestController
@RequestMapping({"/v1/webhooks"})
public class WebhooksController {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public WebhooksController() {}


    @PostMapping(path = "")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<Resource<Entry>> receiveWebhook(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
        @RequestBody JsonNode rawWebhookData) {

        logger.info("Webhook (auth {}) with payload received: {}", authorization, rawWebhookData);

        return Responses.ok(null);
    }

}
