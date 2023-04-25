package fi.digitraffic.tis.vaco.queue;

import org.springframework.stereotype.Service;

@Service
public class QueueService {

    public String processQueueEntry(QueueEntryCommand queueEntryCommand) {
        return "ticketID";
    }

    public QueueEntryView getQueueEntryOutcome(String ticketId) {
        return null;
    }
}
