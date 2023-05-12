package fi.digitraffic.tis.vaco.utils;

import org.springframework.web.bind.annotation.RequestMethod;

public record Link(
        String href,
        RequestMethod method
) { }

