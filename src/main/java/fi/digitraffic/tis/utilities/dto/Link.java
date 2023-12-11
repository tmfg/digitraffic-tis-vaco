package fi.digitraffic.tis.utilities.dto;

import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

public record Link(
        String href,
        RequestMethod method
) {
    public static Link to(String baseUrl, RequestMethod method, UriComponentsBuilder uriComponentsBuilder) {
        URI baseUri = URI.create(baseUrl);
        return new Link(uriComponentsBuilder
            .scheme(baseUri.getScheme())
            .host(baseUri.getHost())
            .port(baseUri.getPort())
            .toUriString(),
            method);
    }
}

