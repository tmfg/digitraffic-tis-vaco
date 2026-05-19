package fi.digitraffic.tis.vaco.ui.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.Hierarchy;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableCompanyInfo.class)
@JsonDeserialize(as = ImmutableCompanyInfo.class)
public interface CompanyInfo {
    Company company();

    List<Context> contexts();

    List<Hierarchy> hierarchies();

    List<Ruleset> rulesets();
}
