package fi.digitraffic.tis.vaco.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.configuration.Aws;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import io.awspring.cloud.autoconfigure.core.AwsClientCustomizer;
import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.acknowledgement.AcknowledgementOrdering;
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode;
import io.awspring.cloud.sqs.support.converter.SqsMessagingMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsAsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.SesClientBuilder;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.net.URI;
import java.time.Duration;

@Configuration
public class AwsConfiguration {

    @Profile("local | compose | tests | itest")
    @Bean
    public AwsCredentialsProvider localCredentials(VacoProperties vacoProperties) {
        Aws aws = vacoProperties.aws();
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(aws.accessKeyId(), aws.secretKey()));
    }

    @Profile("!local & !compose & !tests & !itest")
    @Bean
    public AwsCredentialsProvider cloudCredentials() {
        return DefaultCredentialsProvider.create();
    }


    /**
     * Default timeouts and retries for all service clients
     */
    @Bean
    public ClientOverrideConfiguration clientOverrideConfiguration() {
        return ClientOverrideConfiguration.builder()
            .apiCallAttemptTimeout(Duration.ofSeconds(15))
            .apiCallTimeout(Duration.ofSeconds(15))
            .retryPolicy(retryPolicy -> retryPolicy.numRetries(5))
            .build();
    }

    /**
     * Shared SdkHttpClient for all service clients with default configurations.
     * <p>
     * As per AWS best practices the builder should be shared with unique HTTP client instances being created for each
     * service client.
     */
    @Bean
    public ApacheHttpClient.Builder sdkHttpClientBuilder() {
        return ApacheHttpClient.builder()
            .maxConnections(100)
            .connectionTimeout(Duration.ofSeconds(15))
            .socketTimeout(Duration.ofSeconds(15))
            .connectionAcquisitionTimeout(Duration.ofSeconds(15))
            .connectionMaxIdleTime(Duration.ofSeconds(60));
    }

    /**
     * Shared SdkAsyncHttpClient for all service clients with default configurations.
     */
    @Bean
    public SdkAsyncHttpClient sdkAsyncHttpClient() {
        return NettyNioAsyncHttpClient.builder()
            .connectionTimeout(Duration.ofSeconds(15))
            .build();
    }

    @Bean
    public SqsClient amazonSQSClient(VacoProperties vacoProperties,
                                     AwsCredentialsProvider credentialsProvider,
                                     ClientOverrideConfiguration overrideConfiguration,
                                     ApacheHttpClient.Builder sdkHttpClientBuilder) {
        SqsClientBuilder b = SqsClient.builder()
            .region(Region.of(vacoProperties.aws().region()))
            .credentialsProvider(credentialsProvider)
            .overrideConfiguration(overrideConfiguration);
        if (vacoProperties.aws().endpoint() != null) {
            b = b.endpointOverride(URI.create(vacoProperties.aws().endpoint()));
        }
        return b.httpClientBuilder(sdkHttpClientBuilder).build();
    }

    @Bean
    public SesClient sesClient(VacoProperties vacoProperties,
                               AwsCredentialsProvider credentialsProvider,
                               ClientOverrideConfiguration overrideConfiguration,
                               ApacheHttpClient.Builder sdkHttpClientBuilder) {
        SesClientBuilder b = SesClient.builder()
            .region(Region.of(vacoProperties.aws().region()))
            .credentialsProvider(credentialsProvider)
            .overrideConfiguration(overrideConfiguration);
        if (vacoProperties.aws().endpoint() != null) {
            b = b.endpointOverride(URI.create(vacoProperties.aws().endpoint()));
        }
        return b.httpClientBuilder(sdkHttpClientBuilder).build();
    }

    @Bean
    public S3Client amazonS3Client(VacoProperties vacoProperties,
                                   AwsCredentialsProvider credentialsProvider,
                                   ClientOverrideConfiguration overrideConfiguration,
                                   ApacheHttpClient.Builder sdkHttpClientBuilder) {
        S3ClientBuilder b = S3Client.builder()
            .region(Region.of(vacoProperties.aws().region()))
            .credentialsProvider(credentialsProvider)
            .overrideConfiguration(overrideConfiguration);
        if (vacoProperties.aws().s3() != null) {
            b = b.endpointOverride(URI.create(vacoProperties.aws().s3().endpoint()));
        }
        return b.httpClientBuilder(sdkHttpClientBuilder).build();
    }

    @Bean
    public S3AsyncClient s3AsyncClient(VacoProperties vacoProperties,
                                       AwsCredentialsProvider credentialsProvider,
                                       ClientOverrideConfiguration overrideConfiguration,
                                       SdkAsyncHttpClient sdkAsyncHttpClient) {
        S3AsyncClientBuilder b = S3AsyncClient.builder()
            .region(Region.of(vacoProperties.aws().region()))
            .credentialsProvider(credentialsProvider)
            .overrideConfiguration(overrideConfiguration);
        if (vacoProperties.aws().s3() != null) {
            b = b.endpointOverride(URI.create(vacoProperties.aws().s3().endpoint()));
        }
        return b.httpClient(sdkAsyncHttpClient).build();
    }

    @Bean
    public S3TransferManager s3TransferManager(S3AsyncClient s3AsyncClient) {
        S3TransferManager.Builder b = S3TransferManager.builder();
        return b.s3Client(s3AsyncClient)
            .uploadDirectoryFollowSymbolicLinks(false)
            .build();
    }

    /////// AWSpring config below. These are bound to be deprecated eventually, prefer client configurations above.

    /**
     * This slight delay is used to avoid logspam caused by Spring Cloud AWS' internal logic interpreting zero seconds
     * as a good reason to log dozens of ERROR level stacktraces during shutdown for no good reason.
     */
    private static final Duration ONE_SECOND = Duration.ofSeconds(1);

    @Bean
    SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(
        SqsAsyncClient sqsAsyncClient,
        SqsMessagingMessageConverter messageConverter) {
        return SqsMessageListenerContainerFactory
            .builder()
            .configure(options -> options
                // use manual acknowledgement to ensure each message gets correctly processed at least once
                .acknowledgementMode(AcknowledgementMode.MANUAL)
                .acknowledgementInterval(Duration.ZERO)
                .acknowledgementThreshold(0)
                .acknowledgementOrdering(AcknowledgementOrdering.PARALLEL)
                // because of manual acknowledgement we can shut down immediately
                .listenerShutdownTimeout(ONE_SECOND)
                .pollTimeout(ONE_SECOND)
                .acknowledgementShutdownTimeout(Duration.ZERO)
                .messageConverter(messageConverter)
            )
            .sqsAsyncClient(sqsAsyncClient)
            .build();
    }

    /**
     * Getting around a bug in AWSpring (ref. <a href="https://github.com/awspring/spring-cloud-aws/issues/721"> awspring /
     * spring-cloud-aws #721: Add User's MappingJackson2MessageConverter to SQS Autoconfiguration</a>)
     *
     * @param objectMapper Global <code>ObjectMapper</code> instance everything should use.
     * @return Manually handcrafted, artesanal <code>SqsMessagingMessageConverter</code>
     */
    @Bean
    SqsMessagingMessageConverter sqsMessagingMessageConverter(ObjectMapper objectMapper) {
        SqsMessagingMessageConverter converter = new SqsMessagingMessageConverter();
        MappingJackson2MessageConverter actualConverter = new MappingJackson2MessageConverter();
        actualConverter.setObjectMapper(objectMapper);
        converter.setPayloadMessageConverter(actualConverter);
        return converter;
    }

    @Bean
    AwsClientCustomizer<S3ClientBuilder> s3ClientBuilderAwsClientConfigurer() {
        return new S3AwsClientClientConfigurer();
    }

    @Bean
    public KmsAsyncClient kmsAsyncClient(VacoProperties vacoProperties,
                                         AwsCredentialsProvider credentialsProvider) {
        SdkAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder()
            .maxConcurrency(100)
            .connectionTimeout(Duration.ofSeconds(60))
            .readTimeout(Duration.ofSeconds(60))
            .writeTimeout(Duration.ofSeconds(60))
            .build();

        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
            .apiCallTimeout(Duration.ofMinutes(2))
            .apiCallAttemptTimeout(Duration.ofSeconds(90))
            .retryPolicy(RetryPolicy.builder()
                .numRetries(3)
                .build())
            .build();

        return KmsAsyncClient.builder()
            .httpClient(httpClient)
            .endpointOverride(URI.create("http://localhost:4566"))
            .overrideConfiguration(overrideConfig)
            .region(Region.of(vacoProperties.aws().region()))
            .credentialsProvider(credentialsProvider)
            .build();
    }

    // NOTE: These are actually/probably not use, but should not be removed until entire AWSpring is removed
    static class S3AwsClientClientConfigurer implements AwsClientCustomizer<S3ClientBuilder> {
        @Override
        public ClientOverrideConfiguration overrideConfiguration() {
            return ClientOverrideConfiguration.builder()
                .apiCallAttemptTimeout(Duration.ofSeconds(15))
                .apiCallTimeout(Duration.ofSeconds(15))
                .retryPolicy(retryPolicy -> retryPolicy.numRetries(5))
                .build();
        }

        @Override
        public SdkHttpClient httpClient() {
            return ApacheHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(15))
                .socketTimeout(Duration.ofSeconds(15))
                .connectionAcquisitionTimeout(Duration.ofSeconds(15))
                .connectionMaxIdleTime(Duration.ofSeconds(60))
                .build();
        }

        @Override
        public SdkAsyncHttpClient asyncHttpClient() {
            return NettyNioAsyncHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(15))
                .writeTimeout(Duration.ofSeconds(15))
                .build();
        }
    }
}
