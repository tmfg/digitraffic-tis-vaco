package fi.digitraffic.tis.vaco.db.model;

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
}
