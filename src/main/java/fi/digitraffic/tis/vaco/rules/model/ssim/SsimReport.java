package fi.digitraffic.tis.vaco.rules.model.ssim;

import org.immutables.value.Value;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableSsimReport.class)
@JsonDeserialize(as = ImmutableSsimReport.class)
public interface SsimReport {
    List<SsimFinding> findings();
}
