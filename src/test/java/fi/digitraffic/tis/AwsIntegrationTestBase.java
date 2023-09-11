package fi.digitraffic.tis;

import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

@Testcontainers
public abstract class AwsIntegrationTestBase {

    @Container
    protected static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:2.0.2"))
        .withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.S3);
    protected static S3Client awsS3Client;
    protected static S3AsyncClient s3AsyncClient;
    protected static S3TransferManager s3TransferManager;

    @BeforeAll
    static void awsBeforeAll() {
        // TODO: We should probably favor only the S3AsyncClient for consistency's sake
        awsS3Client = software.amazon.awssdk.services.s3.S3Client.builder()
            .region(Region.of(localstack.getRegion()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        localstack.getAccessKey(),
                        localstack.getSecretKey())))
            .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
            .build();
        s3AsyncClient = S3AsyncClient.crtBuilder()
            .region(Region.of(localstack.getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    localstack.getAccessKey(),
                    localstack.getSecretKey())))
            .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
            .build();
        s3TransferManager = S3TransferManager.builder()
            .s3Client(s3AsyncClient)
            .build();
    }
}
