package fi.digitraffic.tis.vaco.ui.model.pages;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.entries.model.Status;
import org.immutables.value.Value;

import java.time.ZonedDateTime;
import java.util.Optional;

@Value.Immutable
@JsonSerialize(as = fi.digitraffic.tis.vaco.ui.model.pages.ImmutableEntrySummary.class)
@JsonDeserialize(as = fi.digitraffic.tis.vaco.ui.model.pages.ImmutableEntrySummary.class)
public interface EntrySummary {
    @Value.Parameter
    String publicId();

    Optional<String> context();

    @Value.Parameter
    String name();

    @Value.Parameter
    String format();

    @Value.Default
    default Status status() {
        return Status.RECEIVED;
    }

    Optional<ZonedDateTime> created();

    Optional<ZonedDateTime> started();

    Optional<ZonedDateTime> completed();
}
