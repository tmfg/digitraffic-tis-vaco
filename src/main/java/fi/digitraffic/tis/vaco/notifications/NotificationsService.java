package fi.digitraffic.tis.vaco.notifications;

import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationsService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final List<Notifier> notifiers;

    public NotificationsService(List<Notifier> notifiers) {
        this.notifiers = notifiers;
    }

    public void notifyEntryComplete(Entry entry) {
        for (Notifier notifier : notifiers) {
            try {
                notifier.notifyEntryComplete(entry);
            } catch (Exception e) {
                logger.warn("Failed to send EntryComplete notification of {} with {}", entry.publicId(), notifier, e);
            }
        }
    }
}
