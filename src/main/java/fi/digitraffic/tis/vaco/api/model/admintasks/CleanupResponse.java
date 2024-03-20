package fi.digitraffic.tis.vaco.api.model.admintasks;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = fi.digitraffic.tis.vaco.api.model.admintasks.ImmutableCleanupResponse.class)
@JsonDeserialize(as = fi.digitraffic.tis.vaco.api.model.admintasks.ImmutableCleanupResponse.class)
public interface CleanupResponse {

    @Value.Parameter
    List<String> removed();

}
