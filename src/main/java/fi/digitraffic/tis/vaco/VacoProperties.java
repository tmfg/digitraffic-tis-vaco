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

    private final String s3ProcessingBucket;

    private final String uiUrl;
    private final String companyNameClaim;

    public VacoProperties(@DefaultValue("local") String environment,
                          @NotBlank String temporaryDirectory,
                          @NotBlank String s3ProcessingBucket,
                          @NotBlank String uiUrl,
                          @NotBlank String companyNameClaim) {
        this.environment = environment;
        this.temporaryDirectory = temporaryDirectory;
        this.s3ProcessingBucket = s3ProcessingBucket;
        this.uiUrl = uiUrl;
        this.companyNameClaim = companyNameClaim;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getTemporaryDirectory() {
        return temporaryDirectory != null ? temporaryDirectory : System.getProperty("java.io.tmpdir");
    }

    public String getS3ProcessingBucket() {
        return s3ProcessingBucket;
    }

    public String getUiUrl() {
        return uiUrl;
    }

    public String getCompanyNameClaim() {
        return companyNameClaim;
    }
}
