package fi.digitraffic.tis.vaco;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * Examples of how to use Testcontainers in your tests.
 *
 * @see fi.digitraffic.tis.SpringBootIntegrationTestBase
 */
@Testcontainers
public class TestcontainersExamplesTests {

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:2.0.2"))
        .withExposedPorts(4510, 4511, 4512, 4513, 4514); // TODO: the port can have any value between 4510-4559, but LS starts from 4510

    /**
     * AWS S3: Override S3Client settings with Localstack's and then use S3Client as one normally would.
     *
     * Adapted from https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/get-started.html
     */
    @Test
    void s3Example() {
        S3Client s3 = S3Client.builder()
            .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
            .region(Region.of(localstack.getRegion()))
            .build();

        var bucketname = "mybucket";

        s3.createBucket(
            CreateBucketRequest.builder()
                .bucket(bucketname)
                .build());

        var objectKey = "greeting";

        s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucketname)
                .key(objectKey)
                .build(),
            RequestBody.fromString("Hello!"));

        ResponseBytes<GetObjectResponse> s3obj = s3.getObjectAsBytes(GetObjectRequest.builder()
            .bucket(bucketname)
            .key(objectKey)
            .build());

        String data = new String(s3obj.asByteArray());

        assertThat(data, is((equalTo("Hello!"))));
    }

    /**
     * AWS SQS: Override client settings to send and receive messages through Localstack
     *
     * Adapted from https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/java_sqs_code_examples.html
     */
    @Test
    void sqsExample() {
        SqsClient sqsClient = SqsClient.builder()
            .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SQS))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
            .region(Region.of(localstack.getRegion()))
            .build();

        var queueName = "somequeue";
        sqsClient.createQueue(CreateQueueRequest.builder()
            .queueName(queueName)
            .build());

        var queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl();

        var message = "You've got mail!";

        sqsClient.sendMessage(SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(message)
            .build());

        var messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
            .queueUrl(queueUrl)
            .maxNumberOfMessages(5)
            .build()).messages();

        assertThat(messages.get(0).body(), is(equalTo(message)));
    }
}
