package fi.digitraffic.tis.aws.s3;

import fi.digitraffic.tis.utilities.TempFiles;
import fi.digitraffic.tis.vaco.VacoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.config.DownloadFilter;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryDownload;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryUpload;
import software.amazon.awssdk.transfer.s3.model.CompletedFileDownload;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.transfer.s3.model.DownloadDirectoryRequest;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.FileDownload;
import software.amazon.awssdk.transfer.s3.model.UploadDirectoryRequest;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import software.amazon.awssdk.transfer.s3.progress.LoggingTransferListener;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class S3Client {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final S3TransferManager s3TransferManager;

    private final software.amazon.awssdk.services.s3.S3Client awsS3Client;

    private final VacoProperties vacoProperties;

    public S3Client(VacoProperties vacoProperties,
                    S3TransferManager s3TransferManager,
                    software.amazon.awssdk.services.s3.S3Client awsS3Client) {
        this.s3TransferManager = Objects.requireNonNull(s3TransferManager);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.awsS3Client = Objects.requireNonNull(awsS3Client);
    }

    public Path createTempFile(Path downloadDir, String fileName, String extension) {
        Path downloadFile = downloadDir.resolve(fileName + extension);
        if (Files.exists(downloadFile)) {
            throw new S3ClientException("File already exists! Is the process running twice?");
        }
        return downloadFile;
    }

    public Path createVacoDownloadTempFile(String publicId, String format, String taskName) {
        Path downloadDir = TempFiles.getTaskTempDirectory(vacoProperties, publicId, taskName);
        return createTempFile(downloadDir, format, ".download");
    }

    public CompletableFuture<CompletedFileUpload> uploadFile(String bucketName, String targetPath, Path sourcePath) {
        UploadFileRequest ufr = UploadFileRequest.builder()
            .putObjectRequest(req -> req.bucket(bucketName).key(targetPath))
            .addTransferListener(LoggingTransferListener.create())
            .source(sourcePath)
            .build();
        logger.info("Uploading file from {} to s3:{}/{}", sourcePath, bucketName, targetPath);
        return s3TransferManager
            .uploadFile(ufr)
            .completionFuture();
    }

    public CompletableFuture<CompletedDirectoryUpload> uploadDirectory(Path localSourcePath, String bucketName, String s3TargetPath) {
        UploadDirectoryRequest udr = UploadDirectoryRequest.builder()
            .source(localSourcePath)
            .bucket(bucketName)
            .s3Prefix(s3TargetPath)
            .build();
        logger.info("Uploading directory from {} to s3:{}/{}", localSourcePath, bucketName, s3TargetPath);
        return s3TransferManager
            .uploadDirectory(udr)
            .completionFuture();
      }

    public CompletableFuture<CompletedDirectoryDownload> downloadDirectory(String s3Bucket,
                                                                           String s3SourcePath,
                                                                           Path targetPath,
                                                                           String... filterKeys) {
        DownloadDirectoryRequest ddr = DownloadDirectoryRequest.builder()
            .bucket(s3Bucket)
            .listObjectsV2RequestTransformer(l -> l.prefix(s3SourcePath.replaceFirst("^/", "")))
            .filter(filterKeys != null && filterKeys.length > 0
                ? s3Object -> Arrays.stream(filterKeys).noneMatch(filterKey -> s3Object.key().matches(filterKey))
                : DownloadFilter.allObjects())
            .destination(targetPath)
            .build();
        logger.info("Downloading directory from s3:{}{} to {}", vacoProperties.getS3ProcessingBucket(), s3SourcePath, targetPath);
        return s3TransferManager
            .downloadDirectory(ddr)
            .completionFuture();
    }

    // For local debug/test purposes:
    public List<S3Object> listObjectsInBucket(String root, String bucket) {
        ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
            .bucket(bucket)
            .prefix(root)
            .build();
        ListObjectsV2Response listObjectsV2Response = awsS3Client.listObjectsV2(listObjectsV2Request);
        List<S3Object> contents = listObjectsV2Response.contents();
        logger.trace("Number of objects in the bucket: {}", contents.size());
        if (logger.isTraceEnabled()) {
            contents.forEach(s -> logger.trace(s.toString()));
        }
        return contents;
    }

    public byte[] getObjectBytes(String keyName) {
        GetObjectRequest objectRequest = GetObjectRequest
            .builder()
            .key(keyName)
            .bucket(vacoProperties.getS3ProcessingBucket())
            .build();
        ResponseBytes<GetObjectResponse> objectBytes = awsS3Client.getObjectAsBytes(objectRequest);
        return objectBytes.asByteArray();
    }

    public Long downloadFile(String bucketName,
                             String key,
                             Path downloadTargetPath) {
        DownloadFileRequest downloadFileRequest =
            DownloadFileRequest.builder()
                .getObjectRequest(b -> b.bucket(bucketName).key(key))
                .addTransferListener(LoggingTransferListener.create())
                .destination(downloadTargetPath)
                .build();

        FileDownload downloadFile = s3TransferManager.downloadFile(downloadFileRequest);

        CompletedFileDownload downloadResult = downloadFile.completionFuture().join();
        logger.info("Content length [{}]", downloadResult.response().contentLength());

        return downloadResult.response().contentLength();
    }
}

