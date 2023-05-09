package fi.digitraffic.tis.vaco.queuehandler.dto;

import javax.validation.constraints.NotNull;

public record Metadata(
    @NotNull
    String format,

    @NotNull
    String url,

    String etag
) {
}
