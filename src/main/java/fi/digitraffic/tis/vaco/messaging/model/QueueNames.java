package fi.digitraffic.tis.vaco.messaging.model;

/**
 * <code>QueueNames</code> constants should only be used with the {@link io.awspring.cloud.sqs.annotation.SqsListener}
 * annotation! What you're probably looking for is {@link MessageQueue}
 *
 * @see MessageQueue
 */
public final class QueueNames {
    public static final String VACO_JOBS = "vaco-jobs";
    public static final String VACO_JOBS_VALIDATION = VACO_JOBS + "-validation";
    public static final String VACO_RULES_RESULTS = "rules-results";
    public static final String VACO_RULES_PROCESSING_TEMPLATE = "rules-processing-{ruleName}";
    public static final String VACO_ERRORS = "vaco-errors";
    public static final String QLQ = "DLQ-rules-processing";

    private QueueNames() {}
}
