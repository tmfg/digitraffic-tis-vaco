package fi.digitraffic.tis.vaco.ui.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = fi.digitraffic.tis.vaco.ui.model.ImmutableMagicToken.class)
@JsonDeserialize(as = fi.digitraffic.tis.vaco.ui.model.ImmutableMagicToken.class)
public interface MagicToken {
    @Value.Parameter
    String token();
}
