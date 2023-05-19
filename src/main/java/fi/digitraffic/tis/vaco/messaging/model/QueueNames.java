package fi.digitraffic.tis.vaco.messaging.model;

/**
 * <code>QueueNames</code> constants should only be used with the {@link io.awspring.cloud.sqs.annotation.SqsListener}
 * annotation!
 *
 * @see MessageQueue
 */
public class QueueNames {
    public static final String VACO_JOBS = "vaco_jobs";
    public static final String VACO_JOBS_VALIDATION = VACO_JOBS + "_validation";
    public static final String VACO_JOBS_CONVERSION = VACO_JOBS + "_conversion";
}
