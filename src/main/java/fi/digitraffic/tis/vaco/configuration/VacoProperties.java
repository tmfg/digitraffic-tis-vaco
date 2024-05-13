package fi.digitraffic.tis.vaco.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "vaco")
@Validated
public record VacoProperties(@DefaultValue("local") String environment,
                             String temporaryDirectory,
                             @NotBlank String s3ProcessingBucket,
                             @NotBlank String baseUrl,
                             String companyNameClaim,
                             @NestedConfigurationProperty Aws aws,
                             @NestedConfigurationProperty AzureAd azureAd,
                             @NestedConfigurationProperty Email email,
                             @NestedConfigurationProperty MagicLink magicLink,
                             @NestedConfigurationProperty Cleanup cleanup,
                             @NestedConfigurationProperty MsGraph msGraph) {
    @Override
    public String temporaryDirectory() {
        return temporaryDirectory != null ? temporaryDirectory : System.getProperty("java.io.tmpdir");
    }
}
