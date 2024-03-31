package fi.digitraffic.tis.vaco.summary.model;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableSummary.class)
@JsonDeserialize(as = ImmutableSummary.class)
public interface Summary {
    @Nullable
    @JsonView(DataVisibility.InternalOnly.class)
    Long id();

    @Value.Parameter
    @JsonView(DataVisibility.InternalOnly.class)
    Long taskId();

    @Value.Parameter
    String name();

    @Value.Parameter
    RendererType rendererType();

    @Nullable
    @JsonView(DataVisibility.InternalOnly.class)
    @Value.Parameter
    byte[] raw();

    @Nullable
    Object content();
}
