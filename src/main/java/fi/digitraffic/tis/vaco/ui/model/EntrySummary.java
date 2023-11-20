package fi.digitraffic.tis.vaco.ui.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableEntrySummary.class)
@JsonDeserialize(as = ImmutableEntrySummary.class)
public interface EntrySummary {
}
