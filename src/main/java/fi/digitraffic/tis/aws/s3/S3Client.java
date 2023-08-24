package fi.digitraffic.tis.aws.s3;

import fi.digitraffic.tis.vaco.VacoProperties;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.config.DownloadFilter;
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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class S3Client {

    private final S3TransferManager s3TransferManager;

    private final software.amazon.awssdk.services.s3.S3Client awsS3Client;

    private final VacoProperties vacoProperties;

    public S3Client(S3TransferManager s3TransferManager,
                    VacoProperties vacoProperties,
                    software.amazon.awssdk.services.s3.S3Client awsS3Client) {
        this.s3TransferManager = s3TransferManager;
        this.vacoProperties = vacoProperties;
        this.awsS3Client = awsS3Client;
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

    public Path createVacoDownloadTempFile(String publicId, String format, String phaseName) {
        Path downloadDir = Paths.get(vacoProperties.getTemporaryDirectory(), publicId, phaseName);
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

    CompletableFuture<CompletedFileDownload> downloadFile(Path filePath) {
        DownloadFileRequest dfr = DownloadFileRequest.builder()
            .destination(filePath)
            .build();
        return s3TransferManager
            .downloadFile(dfr)
            .completionFuture();
    }

    public CompletableFuture<CompletedDirectoryDownload> downloadDirectory(String sourcePath,
                                                                           String targetPath,
                                                                           String[] filterKeys) {
        DownloadDirectoryRequest ddr = DownloadDirectoryRequest.builder()
            .bucket(getUploadBucketName())
            .listObjectsV2RequestTransformer(l -> l.prefix(sourcePath))
            .filter(filterKeys != null && filterKeys.length > 0
                ? s3Object -> Arrays.stream(filterKeys).noneMatch(filterKey -> s3Object.key().contains(filterKey))
                : DownloadFilter.allObjects())
            .destination(Path.of(targetPath))
            .build();

        return s3TransferManager
            .downloadDirectory(ddr)
            .completionFuture();
    }

    // For local debug/test purposes:
    public void listObjectsInBucket(String root) {
        ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
            .bucket(vacoProperties.getS3processingBucket())
            .prefix(root)
            .build();
        ListObjectsV2Response listObjectsV2Response = awsS3Client.listObjectsV2(listObjectsV2Request);
        List<S3Object> contents = listObjectsV2Response.contents();
        System.out.println("Number of objects in the bucket: " + contents.size());
        contents.forEach(System.out::println);
    }
}

class S3ClientException extends RuntimeException {
    public S3ClientException(String message) {
        super(message);
    }

    public S3ClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
