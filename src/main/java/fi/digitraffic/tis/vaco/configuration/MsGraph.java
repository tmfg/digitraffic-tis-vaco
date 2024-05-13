package fi.digitraffic.tis.vaco.configuration;

import jakarta.validation.constraints.NotBlank;

public record MsGraph(@NotBlank String tenantId,
                      @NotBlank String clientId,
                      @NotBlank String clientSecret,
                      @NotBlank String groupSchemaExtension) {
}
