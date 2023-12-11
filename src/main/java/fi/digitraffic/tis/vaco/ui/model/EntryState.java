package fi.digitraffic.tis.vaco.ui.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableEntryState.class)
@JsonDeserialize(as = ImmutableEntryState.class)
public interface EntryState {

    @Value.Parameter
    Entry entry();

    @Value.Parameter
    @Nullable
    EntrySummary summary();

    List<ValidationReport> validationReports();
}
