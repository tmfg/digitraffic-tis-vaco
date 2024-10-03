package fi.digitraffic.tis.vaco.api.model;

import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.function.Function;

public record Link(
        String href,
        RequestMethod method
) {
    /**
     *
     * @param serverUrl Full base path of the server, including context path if one is set.
     * @param method HTTP method which should be used to call this link.
     * @param linkBuilder Callback to build the actual final link, e.g. for API links from method calls
     * @return Fully formed <code>Link</code> instance
     */
    public static Link to(String serverUrl,
                          RequestMethod method,
                          Function<MvcUriComponentsBuilder, UriComponentsBuilder> linkBuilder) {
        URI baseUri = URI.create(serverUrl);
        UriComponentsBuilder base = UriComponentsBuilder.newInstance()
            .scheme(baseUri.getScheme())
            .host(baseUri.getHost())
            .port(baseUri.getPort())
            .path(baseUri.getPath());
        MvcUriComponentsBuilder builder = MvcUriComponentsBuilder.relativeTo(base);

        return new Link(
            linkBuilder.apply(builder).toUriString(),
            method);
    }
}

