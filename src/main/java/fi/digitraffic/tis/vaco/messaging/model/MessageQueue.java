package fi.digitraffic.tis.vaco.messaging.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum MessageQueue {
    JOBS(QueueNames.VACO_JOBS),
    JOBS_VALIDATION(QueueNames.VACO_JOBS_VALIDATION),
    JOBS_CONVERSION(QueueNames.VACO_JOBS_CONVERSION),
    RULES(QueueNames.VACO_RULES_TEMPLATE),
    ERRORS(QueueNames.VACO_ERRORS),
    DEAD_LETTERS(QueueNames.VACO_DEAD_LETTERS);

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
            .replace(
                "{ruleName}",
                ruleName.replace("_", "-")
                    .replace(".", "_"))
            .replace(' ', '_')
            .toLowerCase();
        logger.trace("Munged {} with {} to {}", queueName, ruleName, munged);
        return munged;
    }
}
