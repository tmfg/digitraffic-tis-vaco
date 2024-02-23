package fi.digitraffic.tis.vaco.ui.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableCompanyWithFormatSummary.class)
@JsonDeserialize(as = ImmutableCompanyWithFormatSummary.class)
public interface CompanyWithFormatSummary {
    @Value.Parameter
    String businessId();

    @Value.Parameter
    String name();

    @Value.Parameter
    @Nullable
    String formatSummary();
}
