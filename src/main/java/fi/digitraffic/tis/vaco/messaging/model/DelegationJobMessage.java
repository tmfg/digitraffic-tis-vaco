package fi.digitraffic.tis.vaco.messaging.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableDelegationJobMessage.class)
@JsonDeserialize(as = ImmutableDelegationJobMessage.class)
public interface DelegationJobMessage extends Retryable {
    Entry entry();
}
