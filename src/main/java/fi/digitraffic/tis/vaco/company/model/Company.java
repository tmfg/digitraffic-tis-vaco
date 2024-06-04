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

    /**
     * @deprecated If you need `id`, use {@link fi.digitraffic.tis.vaco.db.model.CompanyRecord} Otherwise
     *             refer to company uniqueness through {@link #businessId()}
     */
    @Nullable
    @JsonView(DataVisibility.InternalOnly.class)
    @Deprecated(since = "2024-04-08")
    Long id();

    @Value.Parameter
    String businessId();

    @Nullable
    @Value.Parameter
    String name();

    @Value.Default
    @JsonView(DataVisibility.AdminRestricted.class)
    default List<String> contactEmails() {
        return List.of();
    }

    @Value.Default
    default String language() {
        return "fi";
    }

    @Nullable
    @JsonView(DataVisibility.AdminRestricted.class)
    String adGroupId();

    @Value.Parameter
    boolean publish();

    @Value.Default
    default List<String> codespaces() {
        return List.of();
    }
}
