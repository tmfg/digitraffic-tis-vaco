package fi.digitraffic.tis.vaco.ui.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.findings.model.Finding;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableAggregatedFinding.class)
@JsonDeserialize(as = ImmutableAggregatedFinding.class)
public interface AggregatedFinding {

    String code();

    String severity();

    int total();

    @Nullable
    List<Finding> findings();
}
