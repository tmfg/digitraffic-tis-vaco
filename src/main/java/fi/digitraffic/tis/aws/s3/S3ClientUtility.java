package fi.digitraffic.tis.aws.s3;

import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import software.amazon.awssdk.transfer.s3.progress.LoggingTransferListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class S3ClientUtility {

    private final S3TransferManager s3TransferManager;

    public S3ClientUtility(S3TransferManager s3TransferManager) {
        this.s3TransferManager = s3TransferManager;
    }

    public Path createDownloadTempFile(Path downloadDir,
                                       String fileName) {
        try {
            Files.createDirectories(downloadDir);
            Path downloadFile = downloadDir.resolve(fileName + ".download");
            if (Files.exists(downloadFile)) {
                throw new S3ClientUtilityException("File already exists! Is the process running twice?");
            }
            return downloadFile;
        } catch (IOException e) {
            throw new S3ClientUtilityException("Failed to create directories for temporary file, make sure permissions are set correctly for path " + downloadDir, e);
        }
    }

    public CompletableFuture<CompletedFileUpload> uploadFile(String bucketName, String targetPath, Path sourcePath) {
        UploadFileRequest ufr = UploadFileRequest.builder()
            .putObjectRequest(req -> req.bucket(bucketName).key(targetPath))
            .addTransferListener(LoggingTransferListener.create())
            .source(sourcePath)
            .build();
        return s3TransferManager
            .uploadFile(ufr)
            .completionFuture();
    }
}

class S3ClientUtilityException extends RuntimeException {
    public S3ClientUtilityException(String message) {
        super(message);
    }

    public S3ClientUtilityException(String message, Throwable cause) {
        super(message, cause);
    }
}
