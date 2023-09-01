package fi.digitraffic.tis.vaco.conversion;

import fi.digitraffic.tis.vaco.rules.conversion.echo.EchoConverterRule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConversionRuleConfiguration {

    @Bean
    @Qualifier("conversion")
    public EchoConverterRule echoConverterRule() {
        return new EchoConverterRule();
    }
}
