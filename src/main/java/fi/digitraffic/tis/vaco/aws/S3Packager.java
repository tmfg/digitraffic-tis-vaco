package fi.digitraffic.tis.vaco.aws;

import fi.digitraffic.tis.aws.s3.S3Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryDownload;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class S3Packager {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final S3Client s3Client;

    public S3Packager(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Folder where all temporary downloads are stored
     */
    public static final String S3_ROOT_DOWNLOADS_FOLDER = "s3Temp";
    public static final String S3_TEMP_DOWNLOADS_FOLDER = "downloads";

    /**
     * Method to get a temporary folder, unique per packaging request.
     * Looks like: s3Temp/<entryPublicId>_timestamp
     * Under this folder resides:
     *      /downloads with the downloaded S3 artifacts;
     *      <zipFileName>.zip that gets produced from /downloads folder
     * @param entryPublicId
     * @return
     */
    public static String getTempFolderPath(String entryPublicId) {
        return S3_ROOT_DOWNLOADS_FOLDER + "/" + entryPublicId + "_" + LocalDateTime.now();
    }

    public static String getZipPath(String tempFolder, String zipFileName) {
        return tempFolder + "/" + zipFileName + ".zip";
    }

    public static String getDownloadsPath(String tempFolder) {
        return  tempFolder + "/" + S3_TEMP_DOWNLOADS_FOLDER;
    }

    private CompletableFuture<CompletedDirectoryDownload> downloadArtifacts(String s3SourcePath,
                                                                    String[] filterKeys,
                                                                    String tempFolder) throws IOException {
        Path s3DownloadsPath = Path.of(S3_ROOT_DOWNLOADS_FOLDER);
        if(!Files.exists(s3DownloadsPath)) {
            Files.createDirectory(s3DownloadsPath);
        }
        return s3Client.downloadDirectory(s3SourcePath, getDownloadsPath(tempFolder), filterKeys);
    }

    private void createZip(String tempFolder, String zipFileName) throws IOException {
        FileOutputStream fos = new FileOutputStream(getZipPath(tempFolder, zipFileName));
        ZipOutputStream zipOut = new ZipOutputStream(fos);
        File folderToZip = new File(getDownloadsPath(tempFolder));
        addItemToZip(folderToZip, folderToZip.getName(), zipOut);
        zipOut.close();
        fos.close();
    }

    private void addItemToZip(File itemToZip, String itemName, ZipOutputStream zipOut) throws IOException {
        if (itemToZip.isDirectory()) {
            String folderName = itemName.endsWith("/") ? itemName : itemName + "/";
            // Avoiding root "downloads" folder being included in the zip's file structure
            boolean isRootFolder = folderName.equals(S3_TEMP_DOWNLOADS_FOLDER + "/");
            if(!isRootFolder) {
                zipOut.putNextEntry(new ZipEntry(folderName));
                zipOut.closeEntry();
            }
            File[] folderContents = itemToZip.listFiles();
            if(folderContents != null) {
                for (File file : folderContents) {
                    addItemToZip(
                        file,
                        (isRootFolder ? "" : folderName) + file.getName(),
                        zipOut);
                }
            }
            return;
        }
        try (FileInputStream fis = new FileInputStream(itemToZip)) {
            ZipEntry zipEntry = new ZipEntry(itemName);
            zipOut.putNextEntry(zipEntry);
            byte[] bytes = new byte[1024];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }
            zipOut.closeEntry();
        }
    }

    private CompletableFuture<CompletedFileUpload> uploadZip(String entryPublicId, String zipFileName, String zipPath) {
        return s3Client.uploadFile(S3Artifact.getPackagePath(entryPublicId, zipFileName), Path.of(zipPath));
    }

    /**
     * After the zip  is uploaded to its final destination, time to clean up local folder
     */
    void cleanup(File itemToBeDeleted) throws IOException {
        File[] contents = itemToBeDeleted.listFiles();
        if (contents != null) {
            for (File file : contents) {
                cleanup(file);
            }
        }
        Files.delete(itemToBeDeleted.toPath());
    }

    public void producePackage(String entryPublicId,
                               String s3SourcePath,
                               String zipFileName ,
                               String[] filterKeys) {
        CompletableFuture.runAsync(() -> {
            logger.info(String.format("S3Packager starting to package %s artifacts into %s", s3SourcePath, zipFileName));
            String tempFolder = getTempFolderPath(entryPublicId);
            try {
                downloadArtifacts(s3SourcePath, filterKeys, tempFolder).join();
                createZip(tempFolder, zipFileName);
                uploadZip(entryPublicId, zipFileName, getZipPath(tempFolder, zipFileName)).join();
                logger.info(String.format("S3Packager successfully completed packaging %s into %s!", s3SourcePath, zipFileName));
            } catch (IOException e) {
                logger.error(String.format("S3Packager encountered IOException while packaging %s into %s", s3SourcePath, zipFileName), e);
            } finally {
                if (Files.exists(Path.of(tempFolder))) {
                    try {
                        cleanup(new File(tempFolder));
                    } catch (IOException e) {
                        logger.error(String.format("S3Packager has failed cleaning up the temp directory %s produced during packaging %s into %s", tempFolder, s3SourcePath, zipFileName), e);
                    }
                }
            }
        });
    }
}
