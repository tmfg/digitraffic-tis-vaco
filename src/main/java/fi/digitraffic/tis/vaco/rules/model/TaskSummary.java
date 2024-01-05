package fi.digitraffic.tis.vaco.rules.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableTaskSummary.class)
@JsonDeserialize(as = ImmutableTaskSummary.class)
public interface TaskSummary {
    List<String> files();
    List<String> counts();
    List<String> components();
}
