package fi.digitraffic.tis.vaco.summary.model.netex;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.ui.model.summary.Card;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableNetexInputSummary.class)
@JsonDeserialize(as = ImmutableNetexInputSummary.class)
@Value.Style(jdk9Collections = true)
public interface NetexInputSummary {
    List<Card> operators();
    List<Card> lines();
    List<String> files();
    List<String> counts();
}
