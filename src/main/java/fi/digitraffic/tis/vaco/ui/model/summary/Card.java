package fi.digitraffic.tis.vaco.ui.model.summary;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import jakarta.validation.constraints.NotNull;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableCard.class)
@JsonDeserialize(builder = ImmutableCard.Builder.class)
public interface Card {
    String title();

    @NotNull
    Object content();
}
