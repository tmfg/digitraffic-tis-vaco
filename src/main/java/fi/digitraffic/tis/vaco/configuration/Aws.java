package fi.digitraffic.tis.vaco.configuration;

import jakarta.validation.constraints.NotBlank;

import java.util.Optional;

public record Aws (@NotBlank String region,
                   Optional<String> endpoint,
                   String accessKeyId,
                   String secretKey) {

}
