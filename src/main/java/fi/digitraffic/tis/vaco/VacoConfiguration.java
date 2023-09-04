package fi.digitraffic.tis.vaco;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.http.HttpClient;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.time.Duration;

/**
 * Generic didn't-fit-anywhere-else configuration container
 */
@Configuration
public class VacoConfiguration {

    @Autowired
    private S3TransferManager s3TransferManager;

    @Autowired
    private software.amazon.awssdk.services.s3.S3Client awsS3Client;

    @Autowired
    private VacoProperties vacoProperties;

    @Bean
    public HttpClient httpClient() {
        return new HttpClient();
    }

    @Bean
    public S3Client s3ClientUtility() {
        return new S3Client(s3TransferManager, vacoProperties, awsS3Client);
    }

    @Bean(name = "rulesetNameCache")
    public Cache<String, Ruleset> rulesetNameCache() {
        return Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofHours(1))
            .build();
    }
}
