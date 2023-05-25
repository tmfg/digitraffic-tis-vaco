package fi.digitraffic.tis.vaco.aws;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.acknowledgement.AcknowledgementOrdering;
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.time.Duration;

@Configuration
public class AwsConfiguration {

    /**
     * This slight delay is used to avoid logspam caused by Spring Cloud AWS' internal logic interpreting zero seconds
     * as a good reason to log dozens of ERROR level stacktraces during shutdown for no good reason.
     */
    private static final Duration ONE_SECOND = Duration.ofSeconds(1);

    @Bean
    SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(SqsAsyncClient sqsAsyncClient) {
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
            )
            .sqsAsyncClient(sqsAsyncClient)
            .build();
    }
}
