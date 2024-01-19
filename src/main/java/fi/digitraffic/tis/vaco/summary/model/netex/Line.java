package fi.digitraffic.tis.vaco.summary.model.netex;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableLine.class)
@JsonDeserialize(as = ImmutableLine.class)
public interface Line {
    String id();
    String name();
    @Nullable
    String transportMode();
    @Nullable
    String validityStartDate();
    @Nullable
    String validityEndDate();
}
