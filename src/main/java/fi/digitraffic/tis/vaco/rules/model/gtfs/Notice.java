package fi.digitraffic.tis.vaco.rules.model.gtfs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableNotice.class)
@JsonDeserialize(as = ImmutableNotice.class)
public interface Notice {
    String code();

    String severity();

    Long totalNotices();

    List<JsonNode> sampleNotices();
}
