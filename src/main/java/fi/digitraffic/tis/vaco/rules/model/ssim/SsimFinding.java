package fi.digitraffic.tis.vaco.rules.model.ssim;

import org.immutables.value.Value;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableSsimFinding.class)
@JsonDeserialize(as = ImmutableSsimFinding.class)
public interface SsimFinding {
    String code();

    String severity();

    JsonNode context();
}
