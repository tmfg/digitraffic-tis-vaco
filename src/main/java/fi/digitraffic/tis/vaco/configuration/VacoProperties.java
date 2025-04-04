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
                             @NotBlank String contextUrl,
                             String companyNameClaim,
                             @NestedConfigurationProperty Aws aws,
                             @NestedConfigurationProperty AzureAd azureAd,
                             @NestedConfigurationProperty Email email,
                             @NestedConfigurationProperty Cleanup cleanup,
                             @NestedConfigurationProperty MsGraph msGraph,
                             @NestedConfigurationProperty EncryptionKeys encryptionKeys) {
    @Override
    public String temporaryDirectory() {
        return temporaryDirectory != null ? temporaryDirectory : System.getProperty("java.io.tmpdir");
    }
}
