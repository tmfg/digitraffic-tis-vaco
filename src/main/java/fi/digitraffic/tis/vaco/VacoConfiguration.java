package fi.digitraffic.tis.vaco;

import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.http.HttpClient;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

/**
 * Generic didn't-fit-anywhere-else configuration container
 */
@Configuration
public class VacoConfiguration {

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

}
