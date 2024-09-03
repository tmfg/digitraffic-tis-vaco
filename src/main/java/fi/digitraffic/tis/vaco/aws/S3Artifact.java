package fi.digitraffic.tis.vaco.aws;

import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
import fi.digitraffic.tis.aws.s3.S3Path;

public final class S3Artifact {

    static final String ENTRY_ROOT = "entries/%s";

    static final String TASKS_ROOT = ENTRY_ROOT + "/tasks/%s";

    private S3Artifact() {}

    /**
     * Pattern: entries/{entryPublicId}/tasks/validation
     *
     * @param entryPublicId
     * @param task
     * @return
     */
    public static S3Path getTaskPath(String entryPublicId,
                                     String task) {
        return S3Path.of(String.format(TASKS_ROOT, entryPublicId, task));
    }

    public static S3Path getRuleDirectory(String entryPublicId, String taskName, String ruleName) {
        return ImmutableS3Path.builder()
            .from(getTaskPath(entryPublicId, taskName))
            .addPath("rules", ruleName)
            .build();
    }
}
