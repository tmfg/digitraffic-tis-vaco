package fi.digitraffic.tis.vaco.rules.model.gtfs;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableNotice.class)
@JsonDeserialize(builder = ImmutableNotice.Builder.class)
public interface Notice {
    String code();

    String severity();

    Long totalNotices();

    List<JsonNode> sampleNotices();
}
