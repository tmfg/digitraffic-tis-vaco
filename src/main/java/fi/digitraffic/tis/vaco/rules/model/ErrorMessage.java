package fi.digitraffic.tis.vaco.rules.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.findings.model.Finding;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableErrorMessage.class)
@JsonDeserialize(as = ImmutableErrorMessage.class)
public interface ErrorMessage {
    @Value.Parameter
    List<Finding> findings();
}
