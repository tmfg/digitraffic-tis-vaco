package fi.digitraffic.tis.vaco.validation.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.ruleset.model.RulesetType;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableRulesetSubmissionConfiguration.class)
@JsonDeserialize(as = ImmutableRulesetSubmissionConfiguration.class)
public interface RulesetSubmissionConfiguration {
    @Value.Parameter
    RulesetType type();

    @Value.Parameter
    String taskPublicId();
}
