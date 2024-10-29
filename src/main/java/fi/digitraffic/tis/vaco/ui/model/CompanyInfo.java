package fi.digitraffic.tis.vaco.ui.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.LegacyHierarchy;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableCompanyInfo.class)
@JsonDeserialize(as = ImmutableCompanyInfo.class)
public interface CompanyInfo {
    Company company();

    List<Context> contexts();

    List<LegacyHierarchy> hierarchies();

    List<Ruleset> rulesets();
}
