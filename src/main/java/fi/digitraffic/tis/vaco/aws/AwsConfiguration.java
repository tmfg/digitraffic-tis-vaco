package fi.digitraffic.tis.vaco.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.acknowledgement.AcknowledgementOrdering;
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode;
import io.awspring.cloud.sqs.support.converter.SqsMessagingMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
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
}
