package fi.digitraffic.tis.vaco.rules.model.gbfs;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = fi.digitraffic.tis.vaco.rules.model.gbfs.ImmutableFeed.class)
@JsonDeserialize(as = fi.digitraffic.tis.vaco.rules.model.gbfs.ImmutableFeed.class)
public interface Feed {
    String name();
    String url();
}
