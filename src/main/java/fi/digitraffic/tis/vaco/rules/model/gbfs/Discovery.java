package fi.digitraffic.tis.vaco.rules.model.gbfs;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;
import java.util.Map;

/**
 * This class matches the GBFS discovery file.
 *
 * @see <a href="https://github.com/MobilityData/gbfs/blob/v2.3/gbfs.md#json-files">GBFS header information</a>
 */
@Value.Immutable
@JsonSerialize(as = ImmutableDiscovery.class)
@JsonDeserialize(as = ImmutableDiscovery.class)
@Value.Style(forceJacksonPropertyNames = false)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public interface Discovery {
    long lastUpdated();
    int ttl();
    String version();
    Map<String, Map<String, List<Feed>>> data();
}
