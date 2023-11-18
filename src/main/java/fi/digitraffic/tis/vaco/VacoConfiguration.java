package fi.digitraffic.tis.vaco;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.http.HttpClient;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Generic didn't-fit-anywhere-else configuration container
 */
@Configuration
public class VacoConfiguration {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Bean
    public HttpClient httpClient() {
        return new HttpClient();
    }

    @Bean
    public S3Client s3ClientUtility(VacoProperties vacoProperties,
                                    software.amazon.awssdk.services.s3.S3Client awsS3Client,
                                    S3TransferManager s3TransferManager) {
        return new S3Client(vacoProperties, s3TransferManager,awsS3Client);
    }

    @Bean(name = "rulesetNameCache")
    public Cache<String, Ruleset> rulesetNameCache() {
        return Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofHours(1))
            .build();
    }

    @Bean(name = "sqsQueueUrlCache")
    public Cache<String, String> sqsQueueUrlCache() {
        return Caffeine.newBuilder()
            .maximumSize(50)
            .expireAfterWrite(Duration.ofHours(1))
            .build();
    }

    @Bean(name = "packagesCache")
    public Cache<Path, Path> packagesCache() {
        return Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofDays(3))
            .evictionListener(((key, value, cause) -> {
                try {
                    if (key != null) {
                        Files.deleteIfExists((Path) key);
                    }
                } catch (IOException e) {
                    logger.error("Failed to delete file matching to evicted entry '{}' from packagesCache", key, e);
                }
            }))
            .build();
    }
}
