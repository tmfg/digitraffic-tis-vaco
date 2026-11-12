package fi.digitraffic.tis.vaco.ui.model;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.vaco.DataVisibility;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.entries.model.Status;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.time.ZonedDateTime;

@Value.Immutable
@JsonSerialize(as = ImmutableCompanyLatestEntry.class)
@JsonDeserialize(as = ImmutableCompanyLatestEntry.class)
public interface CompanyLatestEntry {
    String companyName();
    String businessId();
    // It's a possibility to have all these as null if company hasn't submitted anything
    @Nullable
    String publicId();
    // Kept out of the JSON, because the data delivery page does not show it. The CSV export and the e-mail
    // notification read the Java field.
    @Nullable
    @JsonView(DataVisibility.InternalOnly.class)
    String feedName();
    @Nullable
    String context();
    @Nullable
    String url();
    @Nullable
    String format();
    @Nullable
    String convertedFormat();
    @Nullable
    Status status();
    @Nullable
    ZonedDateTime created();
}
