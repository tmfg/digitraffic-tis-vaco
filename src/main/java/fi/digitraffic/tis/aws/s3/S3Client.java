package fi.digitraffic.tis.aws.s3;

import fi.digitraffic.tis.vaco.VacoProperties;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryDownload;
import software.amazon.awssdk.transfer.s3.model.CompletedFileDownload;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.transfer.s3.model.DownloadDirectoryRequest;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import software.amazon.awssdk.transfer.s3.progress.LoggingTransferListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

public class S3Client {

    private final S3TransferManager s3TransferManager;

    private final VacoProperties vacoProperties;

    public S3Client(S3TransferManager s3TransferManager, VacoProperties vacoProperties) {
        this.s3TransferManager = s3TransferManager;
        this.vacoProperties = vacoProperties;
    }

    public Path createTempFile(Path downloadDir, String fileName, String extension) {
        try {
            Files.createDirectories(downloadDir);
            Path downloadFile = downloadDir.resolve(fileName + extension);
            if (Files.exists(downloadFile)) {
                throw new S3ClientException("File already exists! Is the process running twice?");
            }
            return downloadFile;
        } catch (IOException e) {
            throw new S3ClientException("Failed to create directories for temporary file, make sure permissions are set correctly for path " + downloadDir, e);
        }
    }

    public Path createVacoDownloadTempFile(String publicId, String format, String taskName) {
        Path downloadDir = Paths.get(vacoProperties.getTemporaryDirectory(), publicId, taskName);
        return createTempFile(downloadDir, format, ".download");
    }

    public String getUploadBucketName() {
        return vacoProperties.getS3processingBucket();
    }

    public CompletableFuture<CompletedFileUpload> uploadFile(String targetPath, Path sourcePath) {
        String bucketName = getUploadBucketName();
        UploadFileRequest ufr = UploadFileRequest.builder()
            .putObjectRequest(req -> req.bucket(bucketName).key(targetPath))
            .addTransferListener(LoggingTransferListener.create())
            .source(sourcePath)
            .build();
        return s3TransferManager
            .uploadFile(ufr)
            .completionFuture();
    }

    CompletableFuture<CompletedDirectoryDownload> downloadDirectory(Path directoryPath) {
        DownloadDirectoryRequest ddr = DownloadDirectoryRequest.builder()
            .destination(directoryPath)
            .build();
        return s3TransferManager.downloadDirectory(ddr)
            .completionFuture();
    }

    CompletableFuture<CompletedFileDownload> downloadFile(Path filePath) {
        DownloadFileRequest dfr = DownloadFileRequest.builder()
            .destination(filePath)
            .build();
        return s3TransferManager
            .downloadFile(dfr)
            .completionFuture();
    }
}

