package fi.digitraffic.tis.vaco.db.model;

import fi.digitraffic.tis.vaco.company.service.model.CompanyRole;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
public interface CompanyRecord {

    @Value.Parameter
    Long id();

    @Value.Parameter
    String businessId();

    @Nullable
    String name();

    @Value.Default
    default List<String> contactEmails() {
        return List.of();
    }

    @Value.Default
    default String language() {
        return "fi";
    }

    @Nullable
    String adGroupId();

    @Value.Parameter
    boolean publish();

    @Value.Default
    default List<String> codespaces() {
        return List.of();
    }

    @Nullable
    String notificationWebhookUri();

    @Nullable
    String website();

    @Value.Default
    default List<CompanyRole> roles() { return List.of(); }
}
