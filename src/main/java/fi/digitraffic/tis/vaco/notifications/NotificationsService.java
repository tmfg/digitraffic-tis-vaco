package fi.digitraffic.tis.vaco.notifications;

import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class NotificationsService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final EntryRepository entryRepository;

    private final List<Notifier> notifiers;

    public NotificationsService(List<Notifier> notifiers, EntryRepository entryRepository) {
        this.notifiers = Objects.requireNonNull(notifiers);
        this.entryRepository = Objects.requireNonNull(entryRepository);
    }

    public void notifyEntryComplete(Entry entry) {
        Optional<EntryRecord> record = entryRepository.findByPublicId(entry.publicId());
        if (record.isPresent()) {
            for (Notifier notifier : notifiers) {
                try {
                    notifier.notifyEntryComplete(record.get());
                } catch (Exception e) {
                    logger.warn("Failed to send EntryComplete notification of {} with {}", entry.publicId(), notifier, e);
                }
            }
        }
    }
}
