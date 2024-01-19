package fi.digitraffic.tis.vaco.ui.model.summary;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableLabelValuePair.class)
@JsonDeserialize(as = ImmutableLabelValuePair.class)
public interface LabelValuePair {
    @Value.Parameter
    @Nullable
    String label();

    @Value.Parameter
    @Nullable
    String value();
}
