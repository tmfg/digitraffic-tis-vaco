package fi.digitraffic.tis.vaco.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "vaco")
@Validated
public record VacoProperties(@DefaultValue("local") String environment,
                             @NotBlank String s3ProcessingBucket,
                             @NotBlank String s3PackagesBucket,
                             @NotBlank String baseUrl,
                             @NotBlank String contextUrl,
                             String companyNameClaim,
                             @NestedConfigurationProperty Aws aws,
                             @NestedConfigurationProperty AzureAd azureAd,
                             @NestedConfigurationProperty Email email,
                             @NestedConfigurationProperty Cleanup cleanup,
                             @NestedConfigurationProperty MsGraph msGraph,
                             @NestedConfigurationProperty EncryptionKeys encryptionKeys) {

    /**
     * Returns the temporary directory path for Vaco.
     * Always resolves to {java.io.tmpdir}/vaco
     *
     * @return the absolute path to the vaco temporary directory
     */
    public String temporaryDirectory() {
        return Path.of(System.getProperty("java.io.tmpdir"), "vaco").toString();
    }
}
