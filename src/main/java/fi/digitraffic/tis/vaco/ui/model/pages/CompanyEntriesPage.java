package fi.digitraffic.tis.vaco.ui.model.pages;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;
import java.util.Optional;

@Value.Immutable
@JsonSerialize(as = fi.digitraffic.tis.vaco.ui.model.pages.ImmutableCompanyEntriesPage.class)
@JsonDeserialize(as = fi.digitraffic.tis.vaco.ui.model.pages.ImmutableCompanyEntriesPage.class)
public interface CompanyEntriesPage {
    /**
     * The name of the company. Empty when the company has no name.
     */
    Optional<String> companyName();

    @Value.Default
    default List<EntrySummary> entries() {
        return List.of();
    }
}
