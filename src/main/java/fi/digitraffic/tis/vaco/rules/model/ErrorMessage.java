package fi.digitraffic.tis.vaco.rules.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.findings.model.Finding;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableErrorMessage.class)
@JsonDeserialize(builder = ImmutableErrorMessage.Builder.class)
public interface ErrorMessage {
    @Value.Parameter
    List<Finding> findings();
}
