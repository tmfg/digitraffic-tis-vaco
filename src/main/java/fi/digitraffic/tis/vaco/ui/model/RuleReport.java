package fi.digitraffic.tis.vaco.ui.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableRuleReport.class)
@JsonDeserialize(as = ImmutableRuleReport.class)
public interface RuleReport {
    String ruleName();

    String ruleDescription();

    Type ruleType();

    List<ItemCounter> findingCounters();

    List<AggregatedFinding> findings();

    List<Resource<Package>> packages();
}
