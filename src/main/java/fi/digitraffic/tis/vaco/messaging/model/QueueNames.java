package fi.digitraffic.tis.vaco.messaging.model;

/**
 * <code>QueueNames</code> constants should only be used with the {@link io.awspring.cloud.sqs.annotation.SqsListener}
 * annotation! What you're probably looking for is {@link MessageQueue}
 *
 * @see MessageQueue
 */
public class QueueNames {
    public static final String VACO_JOBS = "vaco-jobs";
    public static final String VACO_JOBS_VALIDATION = VACO_JOBS + "-validation";
    public static final String VACO_JOBS_CONVERSION = VACO_JOBS + "-conversion";
    public static final String VACO_RULES_TEMPLATE = "vaco-rules-{ruleName}";
    public static final String VACO_DEAD_LETTERS = "vaco-deadletters";
    public static final String VACO_ERRORS = "vaco-errors";
}
