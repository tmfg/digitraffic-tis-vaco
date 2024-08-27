package fi.digitraffic.tis.vaco.messaging.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum MessageQueue {
    JOBS(QueueNames.VACO_JOBS),
    JOBS_VALIDATION(QueueNames.VACO_JOBS_VALIDATION),
    RULE_RESULTS_INGEST(QueueNames.VACO_RULES_RESULTS),
    RULE_PROCESSING(QueueNames.VACO_RULES_PROCESSING_TEMPLATE),
    // TODO: This queue needs to be renamed/migrated with more rigorous process than just direct code renaming as it
    //       requires AWS infra changes as well.

    DLQ(QueueNames.DLQ),
    ERRORS(QueueNames.VACO_ERRORS);

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String queueName;

    MessageQueue(String queueName) {
        this.queueName = queueName;
    }

    public String getQueueName() {
        return queueName;
    }


    /**
     * Reformat given rule name in such way that the result conforms to <a href="https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/quotas-queues.html">SQS queue name limits</a>:
     * <blockquote>
     *  A queue name can have up to 80 characters. The following characters are accepted: alphanumeric characters,
     *  hyphens (-), and underscores (_).
     * </blockquote>
     *
     * @param ruleName
     * @return
     */
    public String munge(String ruleName) {
        String munged = queueName
            .trim()
            .replace(
                "{ruleName}",
                ruleName.replace(".", "-"))
            .replace(' ', '-')
            .trim()
            .toLowerCase();
        logger.trace("Munged {} with {} to {}", queueName, ruleName, munged);
        return munged;
    }
}
