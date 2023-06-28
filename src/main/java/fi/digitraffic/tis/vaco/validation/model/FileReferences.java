package fi.digitraffic.tis.vaco.validation.model;

import jakarta.annotation.Nullable;
import org.immutables.value.Value;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;

import java.nio.file.Path;

/**
 * Wrapper for getting reference to the downloaded file. Keeps both local file reference and S3 path reference in
 * case local file gets reaped before usage for any reason.
 */
@Value.Immutable
public interface FileReferences {
    @Nullable
    @Value.Parameter
    Path localPath();

    @Nullable
    String s3Path();

    @Nullable
    CompletedFileUpload upload();
}
