package fi.digitraffic.tis.vaco.ui.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.packages.model.Package;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableValidationReport.class)
@JsonDeserialize(as = ImmutableValidationReport.class)
public interface ValidationReport {

    String ruleName();

    String ruleDescription();

    List<ItemCounter> counters();

    List<Notice> notices();

    List<Resource<Package>> packages();
}

