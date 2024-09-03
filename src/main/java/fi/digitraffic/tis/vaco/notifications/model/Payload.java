package fi.digitraffic.tis.vaco.notifications.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;

@JsonSubTypes({
    @JsonSubTypes.Type(name = NotificationType.Name.ENTRY_COMPLETE_V1, value = EntryCompletePayload.class)
})
public interface Payload {
}
