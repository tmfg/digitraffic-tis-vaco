package fi.digitraffic.tis.vaco.notifications.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = fi.digitraffic.tis.vaco.notifications.model.ImmutableEntryCompletePayload.class)
@JsonDeserialize(as = fi.digitraffic.tis.vaco.notifications.model.ImmutableEntryCompletePayload.class)
public interface EntryCompletePayload extends Payload {
    String publicId();
}
