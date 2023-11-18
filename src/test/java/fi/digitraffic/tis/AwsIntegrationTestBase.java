package fi.digitraffic.tis;

import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.aws.AwsConfiguration;
import fi.digitraffic.tis.vaco.configuration.Aws;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

@Testcontainers
public abstract class AwsIntegrationTestBase {

    @Container
    protected static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:2.0.2"))
        .withServices(
            LocalStackContainer.Service.SQS,
            LocalStackContainer.Service.S3,
            LocalStackContainer.Service.SES)
        .withEnv("DEFAULT_REGION", Region.EU_NORTH_1.id());
    protected static S3Client awsS3Client;
    protected static S3AsyncClient s3AsyncClient;
    protected static S3TransferManager s3TransferManager;
    protected static SqsClient sqsClient;
    protected static SesClient sesClient;

    @BeforeAll
    static void awsBeforeAll() {
        Region region = Region.of(localstack.getRegion());

        VacoProperties vacoProperties = TestObjects.vacoProperties(
            new Aws(localstack.getRegion(),
                localstack.getEndpoint().toString(),
                localstack.getAccessKey(),
                localstack.getSecretKey()),
            null,
            null);
        // reuse Spring beans without Spring to keep implementations consistent
        AwsConfiguration awsConfiguration = new AwsConfiguration();

        SdkAsyncHttpClient sdkAsyncHttpClient = awsConfiguration.sdkAsyncHttpClient();
        AwsCredentialsProvider credentialsProvider = awsConfiguration.localCredentials(vacoProperties);
        ClientOverrideConfiguration overrideConfiguration = awsConfiguration.clientOverrideConfiguration();
        ApacheHttpClient.Builder sdkHttpClientBuilder = awsConfiguration.sdkHttpClientBuilder();

        awsS3Client = awsConfiguration.amazonS3Client(
            vacoProperties,
            credentialsProvider,
            overrideConfiguration,
            sdkHttpClientBuilder);

        awsS3Client = software.amazon.awssdk.services.s3.S3Client.builder()
            .region(region)
            .credentialsProvider(
                credentialsProvider)
            .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
            .build();

        s3AsyncClient = awsConfiguration.s3AsyncClient(
            vacoProperties,
            credentialsProvider,
            overrideConfiguration,
            sdkAsyncHttpClient);

        s3TransferManager = awsConfiguration.s3TransferManager(s3AsyncClient);

        sqsClient = awsConfiguration.amazonSQSClient(
            vacoProperties,
            credentialsProvider,
            overrideConfiguration,
            sdkHttpClientBuilder);

        sesClient = awsConfiguration.sesClient(
            vacoProperties,
            credentialsProvider,
            overrideConfiguration,
            sdkHttpClientBuilder);

    }

    protected static void createBucket(String bucketName) {
        awsS3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
    }
}
