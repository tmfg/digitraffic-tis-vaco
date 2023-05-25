package fi.digitraffic.tis.vaco.queuehandler.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutablePhase;
import org.immutables.value.Value;

import java.time.LocalDateTime;

@Value.Immutable
@JsonSerialize(as = ImmutablePhase.class)
@JsonDeserialize(as = ImmutablePhase.class)
public interface Phase {
    Long id();
    String name();
    LocalDateTime started();
}
