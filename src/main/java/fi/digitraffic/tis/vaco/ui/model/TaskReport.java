package fi.digitraffic.tis.vaco.ui.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableTaskReport.class)
@JsonDeserialize(as = ImmutableTaskReport.class)
public interface TaskReport {
    String name();

    @Nullable
    String description();

    Type type();

    List<ItemCounter> findingCounters();

    List<AggregatedFinding> findings();

    List<Resource<Package>> packages();
}
