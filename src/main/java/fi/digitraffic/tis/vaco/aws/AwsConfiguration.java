package fi.digitraffic.tis.vaco.aws;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.QueueNotFoundStrategy;
import io.awspring.cloud.sqs.listener.acknowledgement.AcknowledgementOrdering;
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.time.Duration;

@Configuration
public class AwsConfiguration {

    @Bean
    SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(SqsAsyncClient sqsAsyncClient) {
        return SqsMessageListenerContainerFactory
            .builder()
            .configure(options -> options
                // use manual acknowledgement to ensure each message gets correctly processed at least once
                .acknowledgementMode(AcknowledgementMode.MANUAL)
                .acknowledgementInterval(Duration.ofSeconds(3))
                .acknowledgementThreshold(5)
                .acknowledgementOrdering(AcknowledgementOrdering.PARALLEL)
                // do not create queues on the fly
                .queueNotFoundStrategy(QueueNotFoundStrategy.FAIL)
            )
            .sqsAsyncClient(sqsAsyncClient)
            .build();
    }
}
