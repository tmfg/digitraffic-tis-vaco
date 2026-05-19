package fi.digitraffic.tis.vaco.messaging.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableDelegationJobMessage.class)
@JsonDeserialize(builder = ImmutableDelegationJobMessage.Builder.class)
public interface DelegationJobMessage extends JobMessage, Retryable {

}
