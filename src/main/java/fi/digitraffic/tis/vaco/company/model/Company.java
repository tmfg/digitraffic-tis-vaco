package fi.digitraffic.tis.vaco.company.model;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableCompany.class)
@JsonDeserialize(as = ImmutableCompany.class)
public interface Company {

    @Nullable
    @JsonView(DataVisibility.Internal.class)
    Long id();

    @Value.Parameter
    String businessId();

    @Value.Parameter
    String name();

    @Value.Default
    default List<String> contactEmails() {
        return List.of();
    }

    @Value.Default
    default String language() {
        return "fi";
    }
}
