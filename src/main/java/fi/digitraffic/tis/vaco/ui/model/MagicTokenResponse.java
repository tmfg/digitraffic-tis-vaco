package fi.digitraffic.tis.vaco.ui.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableMagicTokenResponse.class)
@JsonDeserialize(as = ImmutableMagicTokenResponse.class)
public interface MagicTokenResponse {
    @Value.Parameter
    String magicLink();
}
