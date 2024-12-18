package fi.digitraffic.tis.utilities;

import fi.digitraffic.exceptions.UnrecoverableIOException;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.RuleExecutionException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Utilities for keeping track of local temporary files. Similar in spirit to {@link fi.digitraffic.tis.vaco.aws.S3Artifact}
 */
public final class TempFiles {

    private TempFiles() {}

    public static Path getPackageDirectory(VacoProperties vacoProperties, Entry entry, Task task, String packageName) {
        return tempDir(vacoProperties,
            Paths.get(
                "entries",
                entry.publicId(),
                "packages",
                task.name(),
                packageName
            ));
    }

    public static Path getTaskTempDirectory(VacoProperties vacoProperties, Entry entry, Task task) {
        return tempDir(vacoProperties,
            Paths.get(
                "entries",
                entry.publicId(),
                "tasks",
                task.name()
            ));
    }

    public static Path getTaskTempFile(Path taskTempDir, String fileName) {
        return tempFile(
            taskTempDir,
            fileName
        );
    }

    public static Path getArtifactDownloadDirectory(VacoProperties vacoProperties, Entry entry) {
        return tempDir(vacoProperties,
            Paths.get(
                "entries",
                entry.publicId(),
                "artifacts",
                "downloads",
                UUID.randomUUID().toString()
            ));
    }

    public static Path getArtifactPackagingFile(VacoProperties vacoProperties, Entry entry, String zipFileName) {
        return tempDir(vacoProperties,
            Paths.get(
                "entries",
                entry.publicId(),
                "artifacts",
                "downloads"
            )).resolve(zipFileName);
    }

    public static Path getRuleTempDirectory(VacoProperties vacoProperties, Entry entry, String taskName, String ruleName) {
        return tempDir(vacoProperties,
            Paths.get(
                "entries",
                entry.publicId(),
                "tasks",
                taskName,
                "rules",
                ruleName
            ));
    }

    private static Path tempDir(VacoProperties vacoProperties, Path path) {
        try {
            Path root = Paths.get(vacoProperties.temporaryDirectory());
            return Files.createDirectories(root.resolve(path));
        } catch (IOException e) {
            throw new UnrecoverableIOException("Failed to create temp file, check application runtime permissions", e);
        }
    }

    private static Path tempFile(Path dir, String file) {
        Path result = dir.resolve(file);
        if (Files.exists(result)) {
            throw new RuleExecutionException("File " + result + " already exists! Is the process running twice?");
        }
        return result;
    }

}
