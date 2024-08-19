package fi.digitraffic.tis.vaco.notifications.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.api.model.Link;
import org.immutables.value.Value;

import java.util.Map;

@Value.Immutable
@JsonSerialize(as = fi.digitraffic.tis.vaco.notifications.model.ImmutableEntryCompletePayload.class)
@JsonDeserialize(as = fi.digitraffic.tis.vaco.notifications.model.ImmutableEntryCompletePayload.class)
public interface EntryCompletePayload extends Payload {

    String publicId();

    String context();

    Map<String, Map<String, Link>> packages();
}
