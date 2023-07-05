package fi.digitraffic.tis.vaco.aws;

public class S3Artifact {

    public static final String ORIGINAL_ENTRY = "/entries/%s/entry.json";

    public static final String METADATA = "/entries/%s/metadata.json";

    static final String DOWNLOAD_PHASE = "/entries/%s/phases/download/%s";

    static final String VALIDATION_PHASE = "/entries/%s/phases/validation/%s/%s";

    static final String CONVERSION_PHASE = "/entries/%s/phases/conversion/%s/%s";

    static final String PACKAGE = "/entries/%s/package/%s.zip";

    static final String ERROR_LOGS = "/entries/%s/logs/errors/%s";

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
     * Pattern: /entries/{entryPublicId}/phases/download/{fileName}.{extension}
     * @param entryPublicId
     * @param artifact: a file name
     * @return
     */
    public static String getDownloadPhasePath(String entryPublicId,
                                              String artifact) {
        return String.format(DOWNLOAD_PHASE, entryPublicId, artifact);
    }

    /**
     * Pattern: /entries/{entryPublicId}/phases/validation/{subPhase}/{fileName}.{extension}
     * @param entryPublicId
     * @param subPhase
     * @param artifact: either a file name or directory with a bunch of files
     * @return
     */
    public static String getValidationPhasePath(String entryPublicId,
                                                String subPhase,
                                                String artifact) {
        return String.format(VALIDATION_PHASE, entryPublicId, subPhase, artifact);
    }

    /**
     * Pattern: /entries/{entryPublicId}/phases/conversion/{subPhase}/{fileName}.{extension}
     * @param entryPublicId
     * @param subPhase
     * @param artifact: either a file name or directory with a bunch of files
     * @return
     */
    public static String getConversionPhasePath(String entryPublicId,
                                                String subPhase,
                                                String artifact) {
        return String.format(CONVERSION_PHASE, entryPublicId, subPhase, artifact);
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
