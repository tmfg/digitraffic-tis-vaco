package fi.digitraffic.tis.vaco.messaging.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.queuehandler.model.QueueEntry;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableJobDescription.class)
@JsonDeserialize(as = ImmutableJobDescription.class)
public interface JobDescription {
    QueueEntry message();
    @Nullable
    String previous();
}
