package fi.digitraffic.tis.vaco;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(value = "vaco.scheduling.enable", havingValue = "true")
public class SchedulingConfiguration {

}
