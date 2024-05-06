package fi.digitraffic.tis.vaco.ui.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.entries.model.Status;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.time.ZonedDateTime;

@Value.Immutable
@JsonSerialize(as = fi.digitraffic.tis.vaco.ui.model.ImmutableMyDataEntrySummary.class)
@JsonDeserialize(as = fi.digitraffic.tis.vaco.ui.model.ImmutableMyDataEntrySummary.class)
public interface MyDataEntrySummary {
    @Value.Parameter
    String publicId();

    @Nullable
    String context();

    @Value.Parameter
    String name();

    @Value.Parameter
    String format();

    @Value.Parameter
    Status status();

    @Nullable
    ZonedDateTime created();

    @Nullable
    ZonedDateTime started();

    @Nullable
    ZonedDateTime updated();

    @Nullable
    ZonedDateTime completed();
}
