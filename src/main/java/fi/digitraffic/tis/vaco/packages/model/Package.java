package fi.digitraffic.tis.vaco.packages.model;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutablePackage.class)
@JsonDeserialize(as = ImmutablePackage.class)
public interface Package {
    @Nullable
    @JsonView(DataVisibility.InternalOnly.class)
    Long id();

    @Nullable
    @JsonView(DataVisibility.InternalOnly.class)
    @Value.Parameter
    Long taskId();

    @Value.Parameter
    String name();

    @JsonView(DataVisibility.InternalOnly.class)
    @Value.Parameter
    String path();
}
