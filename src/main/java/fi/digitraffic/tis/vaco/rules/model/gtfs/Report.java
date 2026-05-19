package fi.digitraffic.tis.vaco.rules.model.gtfs;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableReport.class)
@JsonDeserialize(builder = ImmutableReport.Builder.class)
public interface Report {
    List<Notice> notices();
}
