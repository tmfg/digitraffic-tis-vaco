package fi.digitraffic.tis.vaco.packages.model;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.DomainValue;
import fi.digitraffic.tis.vaco.process.model.Task;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@DomainValue
@Value.Immutable
@JsonSerialize(as = ImmutablePackage.class)
@JsonDeserialize(as = ImmutablePackage.class)
public interface Package {
    /**
     * @deprecated Don't use task reference from here, (re)load within usage context if you need it
     */
    @Deprecated(since = "2024-09-12")
    @Nullable
    @JsonView(DataVisibility.InternalOnly.class)
    @Value.Parameter
    Task task();

    @Value.Parameter
    String name();

    @JsonView(DataVisibility.InternalOnly.class)
    @Value.Parameter
    String path();
}
