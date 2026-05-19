package fi.digitraffic.tis.vaco.ui.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = fi.digitraffic.tis.vaco.ui.model.ImmutableMagicToken.class)
@JsonDeserialize(builder = fi.digitraffic.tis.vaco.ui.model.ImmutableMagicToken.Builder.class)
public interface MagicToken {
    @Value.Parameter
    String token();
}
