package fi.digitraffic.tis;

import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.aws.AwsConfiguration;
import fi.digitraffic.tis.vaco.configuration.Aws;
import fi.digitraffic.tis.vaco.configuration.S3;
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
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

@Testcontainers
public abstract class AwsIntegrationTestBase {

    protected static VacoProperties vacoProperties;

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

        vacoProperties = TestObjects.vacoProperties(
            new Aws(localstack.getRegion(),
                localstack.getEndpoint().toString(),
                localstack.getAccessKey(),
                localstack.getSecretKey(),
                new S3(localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString())),
            null,
            null,
            null,
            null,
            null);
        // reuse Spring beans without Spring to keep implementations consistent
        AwsConfiguration awsConfiguration = new AwsConfiguration();

        SdkAsyncHttpClient sdkAsyncHttpClient = awsConfiguration.sdkAsyncHttpClient();
        AwsCredentialsProvider credentialsProvider = awsConfiguration.localCredentials(vacoProperties);
        ClientOverrideConfiguration overrideConfiguration = awsConfiguration.clientOverrideConfiguration();
        ApacheHttpClient.Builder sdkHttpClientBuilder = awsConfiguration.sdkHttpClientBuilder();

        // S3 clients are special cases because their custom endpoint need when used with Localstack
        awsS3Client = software.amazon.awssdk.services.s3.S3Client.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)
            .overrideConfiguration(overrideConfiguration)
            .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
            .build();

        s3AsyncClient = S3AsyncClient.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)
            .overrideConfiguration(overrideConfiguration)
            .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
            .httpClient(sdkAsyncHttpClient)
            .build();

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

    protected static CreateBucketResponse createBucket(String bucketName) {
        return awsS3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
    }


    protected static CreateQueueResponse createQueue(String queueName){
        return sqsClient.createQueue(CreateQueueRequest.builder().queueName(queueName).build());

    }
}
