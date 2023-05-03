package fi.digitraffic.tis.vaco.queue;

import fi.digitraffic.tis.vaco.queue.entry.QueueEntryCommand;
import fi.digitraffic.tis.vaco.queue.entry.QueueEntryView;
import org.springframework.stereotype.Service;

@Service
public class QueueService {

    public String processQueueEntry(QueueEntryCommand queueEntryCommand) {
        return "publicID";
    }

    public QueueEntryView getQueueEntryView(String ticketId) {
        return null;
    }
}
