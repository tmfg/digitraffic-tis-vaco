package fi.digitraffic.tis.vaco.http.model;

import org.immutables.value.Value;

import java.nio.file.Path;
import java.util.Optional;

@Value.Immutable
public interface DownloadResponse {
    Optional<String> etag();
    Optional<Path> body();
}
