package fi.digitraffic.tis.vaco.ui.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
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
    @Nullable
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
