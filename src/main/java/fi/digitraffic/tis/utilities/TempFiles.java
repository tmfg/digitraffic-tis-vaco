package fi.digitraffic.tis.utilities;

import fi.digitraffic.tis.vaco.VacoProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Utilities for keeping track of local temporary files. Similar in spirit to {@link fi.digitraffic.tis.vaco.aws.S3Artifact}
 */
public class TempFiles {

    public static Path getPackageDirectory(VacoProperties vacoProperties, String entryPublicId, String packageName) {
        return tempDir(vacoProperties,
            Paths.get(
                "entries",
                entryPublicId,
                "packages",
                packageName
            ));
    }

    public static Path getTaskTempDirectory(VacoProperties vacoProperties, String entryPublicId, String taskName) {
        return tempDir(vacoProperties,
            Paths.get(
                "entries",
                entryPublicId,
                "tasks",
                taskName
            ));
    }

    public static Path getArtifactDownloadDirectory(VacoProperties vacoProperties, String entryPublicId) {
        return tempDir(vacoProperties,
            Paths.get(
                "entries",
                entryPublicId,
                "artifacts",
                "downloads",
                UUID.randomUUID().toString()
            ));
    }

    public static Path getArtifactPackagingFile(VacoProperties vacoProperties, String entryPublicId, String zipFileName) {
        return tempDir(vacoProperties,
            Paths.get(
                "entries",
                entryPublicId,
                "artifacts",
                "downloads"
            )).resolve(zipFileName);
    }

    public static Path getRuleTempDirectory(VacoProperties vacoProperties, String entryPublicId, String taskName, String ruleName) {
        return tempDir(vacoProperties,
            Paths.get(
                "entries",
                entryPublicId,
                "tasks",
                taskName,
                "rules",
                ruleName
            ));
    }

    private static Path tempDir(VacoProperties vacoProperties, Path subdir) {
        try {
            Path root = Paths.get(vacoProperties.getTemporaryDirectory());
            return Files.createDirectories(root.resolve(subdir));
        } catch (IOException e) {
            throw new UtilitiesException("Failed to create temp file, check application runtime permissions", e);
        }

    }
}
