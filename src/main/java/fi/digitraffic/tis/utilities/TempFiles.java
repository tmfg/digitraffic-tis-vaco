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

    /**
     * Constants for directory names for temporary file paths.
     */
    private static final class Directory {

        private Directory() {}

        public static final String ENTRIES = "entries";
        public static final String PACKAGES = "packages";
        public static final String TASKS = "tasks";
        public static final String ARTIFACTS = "artifacts";
        public static final String DOWNLOADS = "downloads";
        public static final String RULES = "rules";
    }

    private TempFiles() {}

    public static Path getPackageDirectory(VacoProperties vacoProperties, Entry entry, Task task, String packageName) {
        return tempDir(vacoProperties,
            Paths.get(
                Directory.ENTRIES,
                entry.publicId(),
                Directory.PACKAGES,
                task.name(),
                packageName
            ));
    }

    public static Path getTaskTempDirectory(VacoProperties vacoProperties, Entry entry, Task task) {
        return tempDir(vacoProperties,
            Paths.get(
                Directory.ENTRIES,
                entry.publicId(),
                Directory.TASKS,
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
                Directory.ENTRIES,
                entry.publicId(),
                Directory.ARTIFACTS,
                Directory.DOWNLOADS,
                UUID.randomUUID().toString()
            ));
    }

    public static Path getArtifactPackagingFile(VacoProperties vacoProperties, Entry entry, String zipFileName) {
        return tempDir(vacoProperties,
            Paths.get(
                Directory.ENTRIES,
                entry.publicId(),
                Directory.ARTIFACTS,
                Directory.DOWNLOADS
            )).resolve(zipFileName);
    }

    public static Path getRuleTempDirectory(VacoProperties vacoProperties, Entry entry, String taskName, String ruleName) {
        return tempDir(vacoProperties,
            Paths.get(
                Directory.ENTRIES,
                entry.publicId(),
                Directory.TASKS,
                taskName,
                Directory.RULES,
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
