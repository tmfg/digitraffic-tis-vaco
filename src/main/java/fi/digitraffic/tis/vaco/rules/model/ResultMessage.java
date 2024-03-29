package fi.digitraffic.tis.vaco.rules.model;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import org.immutables.value.Value;

import java.util.List;
import java.util.Map;

@Value.Immutable
@JsonSerialize(as = ImmutableResultMessage.class)
@JsonDeserialize(as = ImmutableResultMessage.class)
public interface ResultMessage {
    @Value.Parameter
    @JsonView(DataVisibility.InternalOnly.class)
    String entryId();

    @Value.Parameter
    @JsonView(DataVisibility.InternalOnly.class)
    Long taskId();

    @Value.Parameter
    String ruleName();

    @Value.Parameter
    String inputs();

    @Value.Parameter
    String outputs();

    @Value.Parameter
    Map<String, List<String>> uploadedFiles();

}
