package fi.digitraffic.tis.vaco.ui.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableProcessingResultsPage.class)
@JsonDeserialize(as = ImmutableProcessingResultsPage.class)
public interface ProcessingResultsPage {
    @Value.Parameter
    String magicLinkToken();
}
