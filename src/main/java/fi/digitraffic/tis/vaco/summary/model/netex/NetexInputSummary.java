package fi.digitraffic.tis.vaco.summary.model.netex;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.ui.model.summary.Card;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableNetexInputSummary.class)
@JsonDeserialize(builder = ImmutableNetexInputSummary.Builder.class)
@Value.Style(jdk9Collections = true)
public interface NetexInputSummary {
    List<Card> operators();
    List<Card> lines();
    List<String> files();
    List<String> counts();
}
