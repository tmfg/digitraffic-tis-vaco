package fi.digitraffic.tis.vaco.utils;

import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;

public class CustomLink extends Link {

    private final String method;


    public CustomLink(String href, LinkRelation rel, String method) {
        super(href, rel);
        this.method = method;
    }

    public String getMethod() {
        return method;
    }
}
