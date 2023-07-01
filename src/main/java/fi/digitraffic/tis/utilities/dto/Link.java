package fi.digitraffic.tis.utilities.dto;

import org.springframework.web.bind.annotation.RequestMethod;

public record Link(
        String href,
        RequestMethod method
) { }

