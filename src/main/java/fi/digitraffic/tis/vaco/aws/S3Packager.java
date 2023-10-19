package fi.digitraffic.tis.vaco.aws;

import fi.digitraffic.tis.aws.s3.AwsS3Exception;
import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.TempFiles;
import fi.digitraffic.tis.utilities.VisibleForTesting;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class S3Packager {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final S3Client s3Client;
    private final VacoProperties vacoProperties;

    public S3Packager(S3Client s3Client,
                      VacoProperties vacoProperties) {
        this.s3Client = Objects.requireNonNull(s3Client);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
    }

    private void createZip(Path sourceFolder, Path targetFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(targetFile.toFile());
             ZipOutputStream zipOut = new ZipOutputStream(fos)) {
            addItemToZip(sourceFolder, sourceFolder, zipOut);
        }
    }

    private void addItemToZip(Path root, Path itemToZip, ZipOutputStream zipOut) throws IOException {
        Path relativized = root.relativize(itemToZip);

        if (Files.isDirectory(itemToZip)) {
            String folderName = relativized.toString();
            // Avoiding root "downloads" folder being included in the zip's file structure
            boolean isRootFolder = folderName.isBlank();
            if (!isRootFolder) {
                zipOut.putNextEntry(new ZipEntry(folderName + "/"));
                zipOut.closeEntry();
            }
            File[] folderContents = itemToZip.toFile().listFiles();
            if (folderContents != null) {
                for (File file : folderContents) {
                    addItemToZip(root, file.toPath(), zipOut);
                }
            }
        } else {
            ZipEntry zipEntry = new ZipEntry(relativized.toString());
            zipOut.putNextEntry(zipEntry);
            Files.copy(itemToZip, zipOut);
            zipOut.closeEntry();
        }
    }

    /**
     * After the zip  is uploaded to its final destination, time to clean up local folder
     */
    @VisibleForTesting
    void cleanup(File itemToBeDeleted) throws IOException {
        File[] contents = itemToBeDeleted.listFiles();
        if (contents != null) {
            for (File file : contents) {
                cleanup(file);
            }
        }
        Files.delete(itemToBeDeleted.toPath());
    }

    public CompletableFuture<Void> producePackage(Entry entry,
                                                  S3Path s3SourcePath,
                                                  S3Path s3TargetPath,
                                                  String zipFileName,
                                                  String... filterKeys) {
        return CompletableFuture.runAsync(() -> {
            Path localArtifactTemp = TempFiles.getArtifactDownloadDirectory(vacoProperties, entry);
            Path localTargetFile = TempFiles.getArtifactPackagingFile(vacoProperties, entry, zipFileName);
            logger.info("Starting to package s3://{}/{} into {}", vacoProperties.s3ProcessingBucket(), s3SourcePath, localTargetFile);
            try {
                s3Client.downloadDirectory(vacoProperties.s3ProcessingBucket(), s3SourcePath, localArtifactTemp, filterKeys).join();
                createZip(localArtifactTemp, localTargetFile);
                S3Path
                    s3FullTargetPath = ImmutableS3Path.builder()
                    .from(s3TargetPath)
                    .addPath(zipFileName)
                    .build();
                s3Client.uploadFile(vacoProperties.s3ProcessingBucket(), s3FullTargetPath, localTargetFile).join();
                logger.info("Successfully completed packaging s3://{}/{} via {} into {}", vacoProperties.s3ProcessingBucket(), s3SourcePath, localArtifactTemp, s3FullTargetPath);
            } catch (IOException e) {
                throw new AwsS3Exception(String.format("Encountered IOException while packaging %s into %s", s3SourcePath, zipFileName), e);
            } finally {
               if (Files.exists(localArtifactTemp)) {
                   try {
                       cleanup(localArtifactTemp.toFile());
                   } catch (IOException e) {
                       logger.error("S3Packager has failed cleaning up the temp directory {} produced during packaging {} into {}", localArtifactTemp, s3SourcePath, zipFileName, e);
                   }
               }
            }
        });
    }
}
