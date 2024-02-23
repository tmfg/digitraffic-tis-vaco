package fi.digitraffic.tis.vaco.queuehandler.mapper;

import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.PersistentEntry;
import org.springframework.stereotype.Component;

@Component
public class PersistentEntryMapper {
    public ImmutableEntry.Builder toEntryBuilder(PersistentEntry persistentEntry) {
        return ImmutableEntry.builder()
            .publicId(persistentEntry.publicId())
            .name(persistentEntry.name())
            .format(persistentEntry.format())
            .url(persistentEntry.url())
            .businessId(persistentEntry.businessId())
            .etag(persistentEntry.etag())
            .metadata(persistentEntry.metadata())
            .notifications(persistentEntry.notifications())
            .created(persistentEntry.created())
            .started(persistentEntry.started())
            .updated(persistentEntry.updated())
            .completed(persistentEntry.completed())
            .status(persistentEntry.status());
    }
}
