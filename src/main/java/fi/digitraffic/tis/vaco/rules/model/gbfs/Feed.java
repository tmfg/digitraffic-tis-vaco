package fi.digitraffic.tis.vaco.rules.model.gbfs;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = fi.digitraffic.tis.vaco.rules.model.gbfs.ImmutableFeed.class)
@JsonDeserialize(builder = fi.digitraffic.tis.vaco.rules.model.gbfs.ImmutableFeed.Builder.class)
public interface Feed {
    String name();
    String url();
}
