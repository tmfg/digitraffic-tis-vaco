package fi.digitraffic.tis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@ConditionalOnProperty(value = "vaco.scheduling.enable", havingValue = "true")
@EnableScheduling
@Configuration
public class SchedulingConfiguration {
}
