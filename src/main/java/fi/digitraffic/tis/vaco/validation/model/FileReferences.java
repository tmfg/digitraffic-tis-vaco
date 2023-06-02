package fi.digitraffic.tis.vaco.validation.model;

import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;

import java.nio.file.Path;

/**
 * Wrapper for getting reference to the downloaded file. Keeps both local file reference and S3 path reference in
 * case local file gets reaped before usage for any reason.
 */
public class FileReferences {
    final Path localPath;
    final String s3Path;
    final CompletedFileUpload upload;

    public FileReferences(Path localPath, String s3Path, CompletedFileUpload upload) {
        this.localPath = localPath;
        this.s3Path = s3Path;
        this.upload = upload;
    }

    public Path localPath() {
        return localPath;
    }

    public String s3Path() {
        return s3Path;
    }

    public CompletedFileUpload upload() {
        return upload;
    }
}
