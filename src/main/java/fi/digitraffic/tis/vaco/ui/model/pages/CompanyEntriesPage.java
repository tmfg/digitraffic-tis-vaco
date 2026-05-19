package fi.digitraffic.tis.vaco.ui.model.pages;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = fi.digitraffic.tis.vaco.ui.model.pages.ImmutableCompanyEntriesPage.class)
@JsonDeserialize(builder = fi.digitraffic.tis.vaco.ui.model.pages.ImmutableCompanyEntriesPage.Builder.class)
public interface CompanyEntriesPage {
    @Value.Default
    default List<EntrySummary> entries() {
        return List.of();
    }
}
