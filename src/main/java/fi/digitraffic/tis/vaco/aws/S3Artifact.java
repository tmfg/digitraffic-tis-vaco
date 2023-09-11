package fi.digitraffic.tis.vaco.aws;

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

    public static String getEntryFolderPath(String entryPublicId) {
        return String.format(ENTRY_ROOT, entryPublicId);
    }

    /**
     * @param entryPublicId
     * @return
     */
    public static String getOriginalEntryPath(String entryPublicId) {
        return String.format(ORIGINAL_ENTRY, entryPublicId);
    }

    /**
     * @param entryPublicId
     * @return
     */
    public static String getMetadataPath(String entryPublicId) {
        return String.format(METADATA, entryPublicId);
    }

    /**
     * Pattern: entries/{entryPublicId}/phases/validation
     * @param entryPublicId
     * @param phase
     * @return
     */
    public static String getPhasePath(String entryPublicId,
                                      String phase) {
        return String.format(TASK_FOLDER, entryPublicId, phase);
    }

    /**
     * Pattern: entries/{entryPublicId}/tasks/validation
     * @param entryPublicId
     * @param task
     * @return
     */
    public static String getDownloadTaskPath(String entryPublicId,
                                             String artifact) {
        return String.format(DOWNLOAD_TASK, entryPublicId, artifact);
    }

    /**
     * Pattern: /entries/{entryPublicId}/tasks/validation/{subTask}/{fileName}.{extension}
     * @param entryPublicId
     * @param subTask
     * @param artifact: either a file name or directory with a bunch of files
     * @return
     */
    public static String getValidationTaskPath(String entryPublicId,
                                               String subTask,
                                               String artifact) {
        return String.format(VALIDATION_TASK, entryPublicId, subTask, artifact);
    }

    /**
     * Pattern: /entries/{entryPublicId}/tasks/conversion/{subTask}/{fileName}.{extension}
     * @param entryPublicId
     * @param subTask
     * @param artifact: either a file name or directory with a bunch of files
     * @return
     */
    public static String getConversionTaskPath(String entryPublicId,
                                               String subTask,
                                               String artifact) {
        return String.format(CONVERSION_TASK, entryPublicId, subTask, artifact);
    }

    /**
     * Pattern: /entries/{entryPublicId}/package/{format}.zip
     * @param entryPublicId
     * @param packageName
     * @return
     */
    public static String getPackagePath(String entryPublicId,
                                        String packageName) {
        return String.format(PACKAGE, entryPublicId, packageName);
    }

    /**
     * Pattern /entries/{entryPublicId}/logs/errors/{fileName}.{extension}
     * @param entryPublicId
     * @param artifact: I guess some kind of json file in most cases?
     * @return
     */
    public static String getErrorLogsPath(String entryPublicId,
                                          String artifact) {
        return String.format(ERROR_LOGS, entryPublicId, artifact);
    }
}
