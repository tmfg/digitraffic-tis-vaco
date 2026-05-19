package fi.digitraffic.tis.vaco.company.model;

import com.fasterxml.jackson.annotation.JsonView;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.DomainValue;
import fi.digitraffic.tis.vaco.company.service.model.CompanyRole;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.util.List;

@DomainValue
@Value.Immutable
@JsonSerialize(as = ImmutableCompany.class)
@JsonDeserialize(builder = ImmutableCompany.Builder.class)
public interface Company {

    @Value.Parameter
    String businessId();

    @Nullable
    @Value.Parameter
    String name();

    @JsonView(DataVisibility.AdminRestricted.class)
    default List<String> contactEmails() {
        return List.of();
    }

    default String language() {
        return "fi";
    }

    @Nullable
    @JsonView(DataVisibility.AdminRestricted.class)
    String adGroupId();

    @Value.Parameter
    boolean publish();

    default List<String> codespaces() {
        return List.of();
    }

    @Nullable
    String notificationWebhookUri();

    @Nullable
    String website();

    default List<CompanyRole> roles() {
        return List.of();
    }
}
