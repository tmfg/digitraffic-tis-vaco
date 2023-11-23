package fi.digitraffic.tis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

/**
 * Beans which use custom configuration which need to be overridden for tests. Try to keep these to minimum!
 *
 * This configuration will cause NPEs if {@link AwsIntegrationTestBase} isn't included in context configuration.
 */
@TestConfiguration
public class OverridesConfiguration {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * AWS SDK builds S3 URIs in rather exciting way which is not test/Localstack compatible. To overcome this, and to
     * allow other tests which use {@link AwsIntegrationTestBase} directly to work as well, this indirect bean override
     * is used to provide the correct endpoint override for the S3 client.
     * @return {@link S3Client} with Localstack/integration testing specific endpoint override.
     */
    @Bean
    public S3Client amazonS3Client() {
        logger.info("S3Client overridden");
        return AwsIntegrationTestBase.awsS3Client;
    }

    /**
     * Same as {@link #amazonS3Client()} but for its asynchronous variant. This is used by e.g.
     * {@link fi.digitraffic.tis.vaco.aws.AwsConfiguration#s3TransferManager(S3AsyncClient)}
     * @return {@link S3AsyncClient} with Localstack/integration testing specific endpoint override.
     */
    @Bean
    public S3AsyncClient amazonS3AsyncClient() {
        logger.info("S3AsyncClient overridden");
        return AwsIntegrationTestBase.s3AsyncClient;
    }

    @Bean
    S3TransferManager s3TransferManager() {
        logger.info("S3TransferManager overridden");
        return AwsIntegrationTestBase.s3TransferManager;
    }
}
