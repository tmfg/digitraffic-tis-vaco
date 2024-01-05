package fi.digitraffic.tis.vaco.ui.model.summary;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableCard.class)
@JsonDeserialize(as = ImmutableCard.class)
public interface Card {
    String title();
    Object content();
}
