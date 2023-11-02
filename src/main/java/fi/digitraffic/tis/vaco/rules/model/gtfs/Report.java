package fi.digitraffic.tis.vaco.rules.model.gtfs;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableReport.class)
@JsonDeserialize(as = ImmutableReport.class)
public interface Report {
    List<Notice> notices();
}
