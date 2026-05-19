package fi.digitraffic.tis.vaco.ui.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableProcessingResultsPage.class)
@JsonDeserialize(as = ImmutableProcessingResultsPage.class)
public interface ProcessingResultsPage {
    @Value.Parameter
    String magicLinkToken();
}
