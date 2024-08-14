package fi.digitraffic.tis.vaco.notifications;

import fi.digitraffic.tis.vaco.notifications.email.EmailNotifier;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.springframework.stereotype.Service;

@Service
public class NotificationsService {
    private final EmailNotifier emailNotifier;

    public NotificationsService(EmailNotifier emailNotifier) {
        this.emailNotifier = emailNotifier;
    }

    public void notifyEntryComplete(Entry entry) {
        emailNotifier.notifyEntryComplete(entry);
    }
}
