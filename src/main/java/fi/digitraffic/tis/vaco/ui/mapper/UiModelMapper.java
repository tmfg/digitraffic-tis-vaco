package fi.digitraffic.tis.vaco.ui.mapper;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.db.model.ContextRecord;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.ui.model.pages.EntrySummary;
import fi.digitraffic.tis.vaco.ui.model.pages.ImmutableEntrySummary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class UiModelMapper {
    public List<EntrySummary> toEntrySummaries(List<Entry> entries) {
        return Streams.collect(entries, this::toEntrySummary);
    }

    public EntrySummary toEntrySummary(Entry entry) {
        return ImmutableEntrySummary.of(entry.publicId(), entry.name(), entry.format())
            .withStatus(entry.status())
            .withContext(Optional.ofNullable(entry.context()))
            .withCreated(Optional.ofNullable(entry.created()))
            .withStarted(Optional.ofNullable(entry.started()))
            .withCompleted(Optional.ofNullable(entry.completed()));
    }

    public EntrySummary toEntrySummary(EntryRecord entry, Optional<ContextRecord> contextRecord) {
        return ImmutableEntrySummary.of(entry.publicId(), entry.name(), entry.format())
            .withStatus(entry.status())
            .withContext(contextRecord.map(ContextRecord::context))
            .withCreated(Optional.ofNullable(entry.created()))
            .withStarted(Optional.ofNullable(entry.started()))
            .withCompleted(Optional.ofNullable(entry.completed()));

    }
}
