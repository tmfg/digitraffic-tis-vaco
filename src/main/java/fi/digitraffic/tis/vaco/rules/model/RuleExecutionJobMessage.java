package fi.digitraffic.tis.vaco.rules.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.messaging.model.JobMessage;
import fi.digitraffic.tis.vaco.messaging.model.Retryable;
import fi.digitraffic.tis.vaco.process.model.Task;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableRuleExecutionJobMessage.class)
@JsonDeserialize(as = ImmutableRuleExecutionJobMessage.class)
public interface RuleExecutionJobMessage<C> extends JobMessage, Retryable {
    Task task();

    String workDirectory();

    @Nullable
    C configuration();
}
