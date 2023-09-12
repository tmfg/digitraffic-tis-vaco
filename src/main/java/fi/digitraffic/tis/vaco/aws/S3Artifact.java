package fi.digitraffic.tis.vaco.aws;

import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
import fi.digitraffic.tis.aws.s3.S3Path;

public class S3Artifact {

    static final String ENTRY_ROOT = "entries/%s";

    public static final String ORIGINAL_ENTRY = ENTRY_ROOT + "/entry.json";

    public static final String METADATA = ENTRY_ROOT + "/metadata.json";

    static final String TASKS_ROOT = ENTRY_ROOT + "/tasks/%s";

    static final String DOWNLOAD_TASK = ENTRY_ROOT + "/tasks/download/%s";

    static final String VALIDATION_TASK = ENTRY_ROOT + "/tasks/validation/%s/%s";

    static final String CONVERSION_TASK = ENTRY_ROOT + "/tasks/conversion/%s/%s";

    static final String PACKAGES_ROOT = ENTRY_ROOT + "/packages";

    static final String PACKAGE = ENTRY_ROOT + "/packages/%s.zip";

    static final String ERROR_LOGS = ENTRY_ROOT + "/logs/errors/%s";

    public static S3Path getEntryFolderPath(String entryPublicId) {
        return ImmutableS3Path.of(String.format(ENTRY_ROOT, entryPublicId));
    }

    /**
     * @param entryPublicId
     * @return
     */
    public static S3Path getOriginalEntryPath(String entryPublicId) {
        return ImmutableS3Path.of(String.format(ORIGINAL_ENTRY, entryPublicId));
    }

    /**
     * @param entryPublicId
     * @return
     */
    public static S3Path getMetadataPath(String entryPublicId) {
        return ImmutableS3Path.of(String.format(METADATA, entryPublicId));
    }

    /**
     * Pattern: entries/{entryPublicId}/tasks/validation
     *
     * @param entryPublicId
     * @param task
     * @return
     */
    public static S3Path getTaskPath(String entryPublicId,
                                     String task) {
        return ImmutableS3Path.of(String.format(TASKS_ROOT, entryPublicId, task));
    }

    /**
     * Pattern: /entries/{entryPublicId}/tasks/conversion/{subTask}/{fileName}.{extension}
     * @param entryPublicId
     * @param subTask
     * @param artifact: either a file name or directory with a bunch of files
     * @return
     */
    public static S3Path getConversionTaskPath(String entryPublicId,
                                               String subTask,
                                               String artifact) {
        return ImmutableS3Path.of(String.format(CONVERSION_TASK, entryPublicId, subTask, artifact));
    }

    public static S3Path getPackagesDirectory(String entryPublicId) {
        return ImmutableS3Path.of(String.format(PACKAGES_ROOT, entryPublicId));
    }

    /**
     * Pattern: /entries/{entryPublicId}/package/{format}.zip
     * @param entryPublicId
     * @param packageName
     * @return
     */
    public static S3Path getPackagePath(String entryPublicId,
                                        String packageName) {
        return ImmutableS3Path.of(getPackagesDirectory(entryPublicId).path() + "/" + packageName);
    }

    /**
     * Pattern /entries/{entryPublicId}/logs/errors/{fileName}.{extension}
     * @param entryPublicId
     * @param artifact: I guess some kind of json file in most cases?
     * @return
     */
    public static S3Path getErrorLogsPath(String entryPublicId,
                                          String artifact) {
        return ImmutableS3Path.of(String.format(ERROR_LOGS, entryPublicId, artifact));
    }

    public static S3Path getRuleDirectory(String entryPublicId, String taskName, String ruleName) {
        return ImmutableS3Path.of(getTaskPath(entryPublicId, taskName).path() + "/rules/" + ruleName);
    }
}
