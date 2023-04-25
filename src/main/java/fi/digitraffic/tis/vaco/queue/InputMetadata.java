package fi.digitraffic.tis.vaco.queue;

import javax.validation.constraints.NotNull;

public record InputMetadata(@NotNull String format,
                            @NotNull String url,
                            @NotNull String etag) {
}
