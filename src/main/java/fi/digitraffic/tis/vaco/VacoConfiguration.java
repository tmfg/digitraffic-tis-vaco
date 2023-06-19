package fi.digitraffic.tis.vaco;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fi.digitraffic.tis.aws.s3.S3ClientUtility;
import fi.digitraffic.tis.http.HttpClientUtility;
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

    @Bean
    public HttpClientUtility httpClient() {
        return new HttpClientUtility();
    }

    @Bean
    public S3ClientUtility s3ClientUtility() {
        return new S3ClientUtility(s3TransferManager);
    }

    @Bean(name = "rulesetNameCache")
    public Cache<String, Ruleset> rulesetNameCache() {
        return Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofHours(1))
            .build();
    }
}
