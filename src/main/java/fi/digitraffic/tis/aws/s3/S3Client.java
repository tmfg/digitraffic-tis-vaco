package fi.digitraffic.tis.aws.s3;

import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.rules.RuleExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedCopy;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryDownload;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryUpload;
import software.amazon.awssdk.transfer.s3.model.CompletedFileDownload;
import software.amazon.awssdk.transfer.s3.model.DownloadDirectoryRequest;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.FileDownload;
import software.amazon.awssdk.transfer.s3.model.UploadDirectoryRequest;
import software.amazon.awssdk.transfer.s3.progress.LoggingTransferListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

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

    public CompletableFuture<PutObjectResponse> uploadFile(String bucketName, S3Path targetPath, Path sourcePath) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Uploading file from {} to s3://{}/{}", sourcePath, bucketName, targetPath);
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(targetPath.toString())
                .build();
            return awsS3Client.putObject(request, sourcePath);
        });
    }

    public CompletableFuture<CompletedDirectoryUpload> uploadDirectory(Path localSourcePath, String bucketName, S3Path s3TargetPath) {
        if (Files.exists(localSourcePath)) {
            UploadDirectoryRequest udr = UploadDirectoryRequest.builder()
                .source(localSourcePath)
                .bucket(bucketName)
                .s3Prefix(s3TargetPath.toString())
                .build();
            logger.info("Uploading directory from {} to s3://{}/{}", localSourcePath, bucketName, s3TargetPath);
            return s3TransferManager
                .uploadDirectory(udr)
                .completionFuture();
        } else {
            logger.info("Source doesn't exist, skipping directory upload from {} to s3://{}/{}", localSourcePath, bucketName, s3TargetPath);
            return CompletableFuture.completedFuture(CompletedDirectoryUpload.builder().build());
        }
    }

    public CompletableFuture<CompletedDirectoryDownload> downloadDirectory(String s3Bucket,
                                                                           S3Path s3SourcePath,
                                                                           Path targetPath,
                                                                           Predicate<String> filter) {
        DownloadDirectoryRequest ddr = DownloadDirectoryRequest.builder()
            .bucket(s3Bucket)
            .listObjectsV2RequestTransformer(l -> l.prefix(s3SourcePath.toString()))
            .filter(s3Object -> filter.test(s3Object.key()))
            .destination(targetPath)
            .build();
        logger.info("Downloading directory from s3://{}/{} to {}", vacoProperties.s3ProcessingBucket(), s3SourcePath, targetPath);
        return s3TransferManager
            .downloadDirectory(ddr)
            .completionFuture();
    }

    // For local debug/test purposes:
    public List<S3Object> listObjectsInBucket(S3Path root, String bucket) {
        ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
            .bucket(bucket)
            .prefix(root.toString())
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
            .bucket(vacoProperties.s3ProcessingBucket())
            .build();
        ResponseBytes<GetObjectResponse> objectBytes = awsS3Client.getObjectAsBytes(objectRequest);
        return objectBytes.asByteArray();
    }

    public Long downloadFile(String bucketName,
                             S3Path key,
                             Path downloadTargetPath) {
        try {
            Files.createDirectories(downloadTargetPath.getParent());
        } catch (IOException e) {
            throw new RuleExecutionException("Failed to create directory for target file " + downloadTargetPath, e);
        }

        DownloadFileRequest downloadFileRequest =
            DownloadFileRequest.builder()
                .getObjectRequest(b -> b.bucket(bucketName).key(key.toString()))
                .addTransferListener(LoggingTransferListener.create())
                .destination(downloadTargetPath)
                .build();

        FileDownload downloadFile = s3TransferManager.downloadFile(downloadFileRequest);

        CompletedFileDownload downloadResult = downloadFile.completionFuture().join();
        logger.info("Content length [{}]", downloadResult.response().contentLength());

        return downloadResult.response().contentLength();
    }

    /**
     * Copies given file in specified bucket from given location to target directory. Both paths are treated as absolute.
     *
     * @param bucket          Bucket in which the copy should occur.
     * @param file            Source file.
     * @param targetDirectory Target directory.
     * @return
     */
    public CompletableFuture<CompletedCopy> copyFile(String bucket, S3Path file, S3Path targetPath) {
        logger.info("Copying object in path s3://{}/{} to s3://{}/{}", bucket, file, bucket, targetPath);
        return s3TransferManager.copy(build ->
                build.copyObjectRequest(copyObject ->
                    copyObject.sourceBucket(bucket)
                        .sourceKey(file.toString())
                        .destinationBucket(bucket)
                        .destinationKey(targetPath.toString())))
            .completionFuture();
    }
}

