package fi.digitraffic.tis.vaco.ui.model.pages;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = fi.digitraffic.tis.vaco.ui.model.pages.ImmutableCompanyEntriesPage.class)
@JsonDeserialize(as = fi.digitraffic.tis.vaco.ui.model.pages.ImmutableCompanyEntriesPage.class)
public interface CompanyEntriesPage {
    @Value.Default
    default List<EntrySummary> entries() {
        return List.of();
    }
}
