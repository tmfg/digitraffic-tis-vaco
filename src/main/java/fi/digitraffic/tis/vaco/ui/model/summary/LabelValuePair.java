package fi.digitraffic.tis.vaco.ui.model.summary;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableLabelValuePair.class)
@JsonDeserialize(builder = ImmutableLabelValuePair.Builder.class)
public interface LabelValuePair {
    @Value.Parameter
    @Nullable
    String label();

    @Value.Parameter
    @Nullable
    String value();
}
