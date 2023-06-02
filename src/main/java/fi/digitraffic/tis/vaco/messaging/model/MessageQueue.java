package fi.digitraffic.tis.vaco.messaging.model;

public enum MessageQueue {
    JOBS(QueueNames.VACO_JOBS),
    JOBS_VALIDATION(QueueNames.VACO_JOBS_VALIDATION),
    JOBS_CONVERSION(QueueNames.VACO_JOBS_CONVERSION);

    private final String queueName;

    MessageQueue(String queueName) {
        this.queueName = queueName;
    }

    public String getQueueName() {
        return queueName;
    }
}
