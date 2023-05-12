package fi.digitraffic.tis.vaco;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Customize Spring's internal Jackson facilities.
 */
@Configuration
public class JacksonFeaturesConfiguration {

    /**
     * Set coercion of types to be as strict as possible.
     *
     * @return <code>Jackson2ObjectMapperBuilder</code> with strict {@link com.fasterxml.jackson.databind.cfg.CoercionConfig}
     */
    @Bean
    public Jackson2ObjectMapperBuilder jackson2ObjectMapperBuilder() {
        return new Jackson2ObjectMapperBuilder()
                .postConfigurer((objectMapper -> {
                    objectMapper.coercionConfigDefaults()
                            .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                            .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                            .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                            .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                            .setCoercion(CoercionInputShape.Array, CoercionAction.Fail)
                            .setCoercion(CoercionInputShape.Object, CoercionAction.Fail);
                }));
    }
}
