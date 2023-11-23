package fi.digitraffic.tis.vaco.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public record Aws (@NotBlank String region,
                   String endpoint,
                   String accessKeyId,
                   String secretKey,
                   @NestedConfigurationProperty S3 s3) {

}
