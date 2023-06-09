package fi.digitraffic.tis.vaco.messaging;

import fi.digitraffic.tis.vaco.messaging.model.ImmutableJobDescription;
import fi.digitraffic.tis.vaco.messaging.model.JobDescription;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.queuehandler.model.ProcessingState;

public interface MessagingService {
    void sendMessage(MessageQueue messageQueue, JobDescription jobDescription);

    void updateJobProcessingStatus(ImmutableJobDescription jobDescription, ProcessingState start);
}
