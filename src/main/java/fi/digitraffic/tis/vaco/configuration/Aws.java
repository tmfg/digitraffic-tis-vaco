package fi.digitraffic.tis.vaco.configuration;

import jakarta.validation.constraints.NotBlank;

public record Aws (@NotBlank String region,
                   String endpoint,
                   String accessKeyId,
                   String secretKey) {

}
