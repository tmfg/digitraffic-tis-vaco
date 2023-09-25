package fi.digitraffic.tis.vaco.messaging.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableDelegationJobMessage.class)
@JsonDeserialize(as = ImmutableDelegationJobMessage.class)
public interface DelegationJobMessage extends JobMessage, Retryable {

}
