package fi.digitraffic.tis.vaco;

import fi.digitraffic.http.HttpClient;
import fi.digitraffic.http.HttpClientConfiguration;
import fi.digitraffic.http.ImmutableHttpClientConfiguration;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.fintrafficid.FintrafficIdService;
import fi.digitraffic.tis.vaco.fintrafficid.MsGraphBackedFintrafficIdService;
import fi.digitraffic.tis.vaco.http.VacoHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

/**
 * Generic didn't-fit-anywhere-else configuration container
 */
@Configuration
@PropertySource(value = "classpath:git.properties", ignoreResourceNotFound = true)
public class VacoConfiguration {

    @Bean
    public VacoHttpClient httpClient(VacoProperties vacoProperties,
                                     @Value("${git.commit.id.abbrev:#{null}}") String gitCommitIdAbbreviation) {
        HttpClientConfiguration configuration = ImmutableHttpClientConfiguration.builder()
            .baseUri(vacoProperties.baseUrl())
            .userAgentExtension("VACO/" + ((gitCommitIdAbbreviation != null) ? gitCommitIdAbbreviation : vacoProperties.environment()))
            .build();
        return new VacoHttpClient(new HttpClient(configuration));
    }

    @Bean
    public S3Client s3ClientUtility(VacoProperties vacoProperties,
                                    software.amazon.awssdk.services.s3.S3Client awsS3Client,
                                    S3TransferManager s3TransferManager) {
        return new S3Client(vacoProperties, s3TransferManager,awsS3Client);
    }

    @ConditionalOnProperty(name = "vaco.ms-graph.client-secret")
    @Bean
    public FintrafficIdService fintrafficIdService(VacoProperties vacoProperties, CachingService cachingService) {
        return new MsGraphBackedFintrafficIdService(vacoProperties, cachingService);
    }
}
