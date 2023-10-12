package fi.digitraffic.tis.vaco.configuration;

import jakarta.validation.constraints.NotBlank;

public record AzureAd(@NotBlank String tenantId,
                      @NotBlank String clientId) {}
