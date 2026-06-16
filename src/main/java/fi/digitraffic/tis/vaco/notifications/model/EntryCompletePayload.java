package fi.digitraffic.tis.vaco.notifications.model;

import com.fasterxml.jackson.annotation.JsonView;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.api.model.Link;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.immutables.value.Value;

import java.util.Map;

@Value.Immutable
@JsonSerialize(as = fi.digitraffic.tis.vaco.notifications.model.ImmutableEntryCompletePayload.class)
@JsonDeserialize(as = fi.digitraffic.tis.vaco.notifications.model.ImmutableEntryCompletePayload.class)
public interface EntryCompletePayload extends Payload {

    @JsonView(DataVisibility.Webhook.class)
    Entry entry();

    @JsonView(DataVisibility.Webhook.class)
    Map<String, Map<String, Link>> packages();
}
