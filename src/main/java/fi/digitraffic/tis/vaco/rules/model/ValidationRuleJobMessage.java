package fi.digitraffic.tis.vaco.rules.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.messaging.model.JobMessage;
import fi.digitraffic.tis.vaco.messaging.model.Retryable;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.rules.RuleConfiguration;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableValidationRuleJobMessage.class)
@JsonDeserialize(as = ImmutableValidationRuleJobMessage.class)
public interface ValidationRuleJobMessage extends JobMessage, Retryable {
    Task task();

    String inputs();
    String outputs();

    String source();

    @Nullable
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "source")
    RuleConfiguration configuration();
}
