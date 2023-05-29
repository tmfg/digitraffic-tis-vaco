package fi.digitraffic.tis.vaco;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "vaco")
@Validated
public class VacoProperties {

    private final String environment;

    private final String temporaryDirectory;

    private final String s3processingBucket;

    public VacoProperties(@DefaultValue("dev") String environment,
                          @NotBlank String temporaryDirectory,
                          @NotBlank String s3processingBucket) {
        this.environment = environment;
        this.temporaryDirectory = temporaryDirectory;
        this.s3processingBucket = s3processingBucket;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getTemporaryDirectory() {
        return temporaryDirectory;
    }

    public String getS3processingBucket() {
        return s3processingBucket;
    }
}
