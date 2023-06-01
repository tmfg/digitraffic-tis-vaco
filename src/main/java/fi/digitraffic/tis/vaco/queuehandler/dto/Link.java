package fi.digitraffic.tis.vaco.queuehandler.dto;

import org.springframework.web.bind.annotation.RequestMethod;

public record Link(
        String href,
        RequestMethod method
) { }

