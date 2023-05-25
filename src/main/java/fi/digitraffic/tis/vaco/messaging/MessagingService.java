package fi.digitraffic.tis.vaco.messaging;

import fi.digitraffic.tis.vaco.messaging.model.JobDescription;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;

public interface MessagingService {
    void sendMessage(MessageQueue messageQueue, JobDescription jobDescription);
}
