package fi.digitraffic.tis.vaco.webhooks;

import com.fasterxml.jackson.databind.JsonNode;
import fi.digitraffic.http.HttpClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

/**
 * Simple delegate to allow for easier mocking.
 */
@Component
public class SpringWebClientShim {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final WebClient httpClient;

    public SpringWebClientShim(WebClient httpClient) {
        this.httpClient = httpClient;
    }

    public <T> Optional<JsonNode> executeRequest(HttpMethod method, String uri, Map<String, String> headers) {
        return executeRequest(method, uri, headers, Optional.empty());
    }

    public <T> Optional<JsonNode> executeRequest(HttpMethod method, String uri, Map<String, String> headers, Optional<T> body) {
        try {
            logger.info("Executing request [{} {}]", method, uri);
            WebClient.RequestBodySpec requestSpec = httpClient.method(method)
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .headers(requestHeaders -> headers.forEach(requestHeaders::set));

            WebClient.RequestHeadersSpec<?> request;
            if (body.isPresent()) {
                T requestBody = body.get();
                logger.debug("Will send request body {}", requestBody);
                request = requestSpec.bodyValue(requestBody);
            } else {
                request = requestSpec;
            }

            return request
                .retrieve()
                .toEntity(JsonNode.class)
                .flatMap(entity -> {
                    HttpStatusCode statusCode = entity.getStatusCode();
                    logger.debug("HTTP {} {} returned {}", method, uri, statusCode.value());
                    if (statusCode.is2xxSuccessful()) {
                        return Mono.justOrEmpty(entity.getBody());
                    } else {
                        return Mono.empty();
                    }
                })
                .blockOptional();
        } catch (Exception e) {
            throw new HttpClientException("Failed to execute request " + method + " " + uri, e);
        }
    }
}
